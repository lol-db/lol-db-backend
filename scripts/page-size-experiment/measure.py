"""세 컨테이너에서 5개 쿼리를 EXPLAIN ANALYZE로 측정하고 저장 공간·buffer pool 지표를 모은다.

  python3 -I measure.py <시나리오 이름>      예) default, bp-min
결과: work/results-<시나리오>.json

- 콜드: 쿼리마다 컨테이너 재시작 + Docker VM의 OS 페이지 캐시 비우기 → 첫 실행 1회
- 웜: 콜드 측정 뒤 같은 쿼리를 20회 연속 실행 (평균·최소·최대)
- 시간은 EXPLAIN ANALYZE 최상위 노드의 actual time(마지막 행까지, ms)
"""
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT_PW = "pagesize-exp"
DB = "riftdata"
TARGETS = [("ps-8k", 8192), ("ps-16k", 16384), ("ps-32k", 32768)]
QUERIES = sorted(f for f in os.listdir(os.path.join(HERE, "queries")) if f.endswith(".sql"))
WARM_RUNS = 20
TABLES = ["team", "player", "champion", "stage", "lck_match", "match_set", "team_set",
          "player_set", "ban", "player_season"]
TIME_RE = re.compile(r"actual time=([\d.]+)\.\.([\d.]+)")


def mysql(container, sql):
    out = subprocess.run(
        ["docker", "exec", "-i", container, "mysql", "-uroot", f"-p{ROOT_PW}", "-N", "-B", DB],
        input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(f"{container}: {out.stderr.strip()}")
    return out.stdout


def status(container):
    rows = mysql(container, "SHOW GLOBAL STATUS WHERE Variable_name IN "
                            "('Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests')")
    return {k: int(v) for k, v in (line.split("\t") for line in rows.strip().split("\n"))}


def explain_ms(container, sql):
    plan = mysql(container, "EXPLAIN ANALYZE " + sql)
    return float(TIME_RE.search(plan).group(2)), plan


def wait_ready(container):
    for _ in range(90):
        r = subprocess.run(["docker", "exec", container, "mysql", "-uroot", f"-p{ROOT_PW}", "-e", "SELECT 1"],
                           capture_output=True)
        if r.returncode == 0:
            return
        time.sleep(1)
    raise RuntimeError(f"{container} not ready")


def cold_restart(container):
    subprocess.run(["docker", "restart", container], check=True, capture_output=True)
    wait_ready(container)
    # Docker VM의 OS 페이지 캐시를 비운다 (InnoDB 기본 flush 방식은 OS 캐시를 거치므로)
    subprocess.run(["docker", "run", "--rm", "--privileged", "--pid=host", "alpine:3.20",
                    "sh", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches"], check=True, capture_output=True)


def delta(before, after):
    reads = after["Innodb_buffer_pool_reads"] - before["Innodb_buffer_pool_reads"]
    reqs = after["Innodb_buffer_pool_read_requests"] - before["Innodb_buffer_pool_read_requests"]
    return {"reads": reads, "read_requests": reqs, "disk_read_ratio": reads / reqs if reqs else None}


def index_stats(container):
    """ANALYZE 후 인덱스별 실제 리프 페이지 수(n_leaf_pages)와 할당 페이지 수(size)"""
    rows = mysql(container, "SELECT table_name, index_name, stat_name, stat_value FROM mysql.innodb_index_stats "
                            f"WHERE database_name='{DB}' AND stat_name IN ('n_leaf_pages','size')")
    out = {}
    for line in rows.strip().split("\n"):
        t, i, k, v = line.split("\t")
        out.setdefault(t, {}).setdefault(i, {})[k] = int(v)
    return out


def main():
    scenario = sys.argv[1]
    if scenario == "sizes-only":  # 측정 없이 인덱스 통계만 (work/index-stats.json)
        res = {}
        for name, _ in TARGETS:
            mysql(name, "ANALYZE TABLE " + ",".join(TABLES))
            res[name] = index_stats(name)
        with open(os.path.join(HERE, "work", "index-stats.json"), "w") as f:
            json.dump(res, f, indent=1)
        return
    result = {"scenario": scenario, "containers": {}}
    for name, page in TARGETS:
        info = mysql(name, "SELECT VERSION(), @@innodb_page_size, @@innodb_buffer_pool_size, "
                           "@@innodb_flush_method, @@innodb_buffer_pool_load_at_startup").strip().split("\t")
        c = {"version": info[0], "page_size": int(info[1]), "buffer_pool_size": int(info[2]),
             "flush_method": info[3], "load_at_startup": info[4], "queries": {}}
        for qf in QUERIES:
            sql = open(os.path.join(HERE, "queries", qf)).read().strip()
            cold_restart(name)
            s0 = status(name)
            cold, plan = explain_ms(name, sql)
            s1 = status(name)
            warm = [explain_ms(name, sql)[0] for _ in range(WARM_RUNS)]
            s2 = status(name)
            rows = len(mysql(name, sql).strip().split("\n"))
            c["queries"][qf] = {"cold_ms": cold, "warm_ms": warm, "rows": rows,
                                "cold_bp": delta(s0, s1), "warm_bp": delta(s1, s2), "plan": plan}
            print(f"{scenario} {name} {qf}: cold {cold:.3f} ms, warm avg {sum(warm)/len(warm):.3f} ms", flush=True)
        # 저장 공간
        mysql(name, "ANALYZE TABLE " + ",".join(TABLES))
        sizes = {}
        for t in TABLES:
            dl, il = mysql(name, f"SELECT DATA_LENGTH, INDEX_LENGTH FROM information_schema.TABLES "
                                 f"WHERE TABLE_SCHEMA='{DB}' AND TABLE_NAME='{t}'").strip().split("\t")
            cnt = int(mysql(name, f"SELECT COUNT(*) FROM {t}").strip())
            ibd = int(subprocess.run(["docker", "exec", name, "stat", "-c", "%s", f"/var/lib/mysql/{DB}/{t}.ibd"],
                                     capture_output=True, text=True).stdout.strip())
            sizes[t] = {"rows": cnt, "data_length": int(dl), "index_length": int(il), "ibd_bytes": ibd}
        c["tables"] = sizes
        c["index_stats"] = index_stats(name)
        bp = mysql(name, "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_buffer_pool_pages_total',"
                         "'Innodb_buffer_pool_pages_data','Innodb_buffer_pool_pages_free')")
        c["bp_pages"] = {k: int(v) for k, v in (l.split("\t") for l in bp.strip().split("\n"))}
        result["containers"][name] = c
    os.makedirs(os.path.join(HERE, "work"), exist_ok=True)
    with open(os.path.join(HERE, "work", f"results-{scenario}.json"), "w") as f:
        json.dump(result, f, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    main()
