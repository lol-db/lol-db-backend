"""work/results-*.json을 읽어 문서용 표(마크다운)를 출력한다.  python3 -I report.py"""
import json
import os
import statistics as st

HERE = os.path.dirname(os.path.abspath(__file__))
C = ["ps-8k", "ps-16k", "ps-32k"]
LABEL = {"q1_match_list.sql": "경기 목록", "q2_set_detail.sql": "세트 상세 (스코어보드)",
         "q3_standings.sql": "순위표 계산", "q4_player_ranking.sql": "선수 랭킹 (시즌 세트 평점)",
         "q5_player_games.sql": "선수 경기 기록"}


def load(name):
    with open(os.path.join(HERE, "work", f"results-{name}.json")) as f:
        return json.load(f)["containers"]


def warm_table(runs, title):
    d = load(runs[0])
    print(f"\n#### {title}: 웜 20회 평균 (최소~최대), ms — {runs[0]}")
    print("| 쿼리 | 결과 행 | 8KB | 16KB | 32KB |\n| --- | ---: | ---: | ---: | ---: |")
    for q in LABEL:
        cells = []
        for c in C:
            w = d[c]["queries"][q]["warm_ms"]
            cells.append(f"{st.mean(w):.3f} ({min(w):.3f}~{max(w):.3f})")
        print(f"| {LABEL[q]} | {d['ps-16k']['queries'][q]['rows']} | " + " | ".join(cells) + " |")
    print(f"\n#### {title}: 반복 3회의 웜 평균, ms — {', '.join(runs)}")
    print("| 쿼리 | 8KB | 16KB | 32KB |\n| --- | ---: | ---: | ---: |")
    ds = [load(r) for r in runs]
    for q in LABEL:
        cells = [" / ".join(f"{st.mean(x[c]['queries'][q]['warm_ms']):.3f}" for x in ds) for c in C]
        print(f"| {LABEL[q]} | " + " | ".join(cells) + " |")


def cold_table(runs, title):
    ds = [load(r) for r in runs]
    print(f"\n#### {title}: 콜드 첫 실행, ms (반복 1회차 / 2회차 / 3회차)")
    print("| 쿼리 | 8KB | 16KB | 32KB |\n| --- | ---: | ---: | ---: |")
    for q in LABEL:
        cells = [" / ".join(f"{x[c]['queries'][q]['cold_ms']:.2f}" for x in ds) for c in C]
        print(f"| {LABEL[q]} | " + " | ".join(cells) + " |")


def bp_table(name, title):
    d = load(name)
    print(f"\n#### {title}: buffer pool 읽기 — {name}")
    print("| 쿼리 | 구분 | 8KB reads / requests | 16KB reads / requests | 32KB reads / requests |\n| --- | --- | --- | --- | --- |")
    for q in LABEL:
        for phase, key in [("콜드 1회", "cold_bp"), ("웜 20회", "warm_bp")]:
            cells = []
            for c in C:
                b = d[c]["queries"][q][key]
                cells.append(f"{b['reads']} / {b['read_requests']} ({100 * b['reads'] / b['read_requests']:.2f}%)")
            print(f"| {LABEL[q]} | {phase} | " + " | ".join(cells) + " |")


def size_table(name):
    d = load(name)
    with open(os.path.join(HERE, "work", "index-stats.json")) as f:
        ix = json.load(f)
    print("\n#### 저장 공간: 테이블별 기본 키(클러스터드 인덱스) 리프 페이지 (mysql.innodb_index_stats, ANALYZE 후)")
    print("| 테이블 | 행 수 | 8KB 리프 페이지 (행/페이지) | 16KB | 32KB |\n| --- | ---: | ---: | ---: | ---: |")
    tables = d["ps-16k"]["tables"]
    for t in sorted(tables, key=lambda t: -tables[t]["rows"]):
        cells = []
        for c in C:
            leaf = ix[c][t]["PRIMARY"]["n_leaf_pages"]
            cells.append(f"{leaf} ({tables[t]['rows'] / leaf:.1f})")
        print(f"| {t} | {tables[t]['rows']} | " + " | ".join(cells) + " |")
    print("\n| 합계 | 8KB | 16KB | 32KB |\n| --- | ---: | ---: | ---: |")
    leaf = {c: sum(i["n_leaf_pages"] for t in ix[c].values() for i in t.values()) for c in C}
    print("| 리프 페이지 수 (모든 인덱스) | " + " | ".join(f"{leaf[c]:,}" for c in C) + " |")
    print("| 리프 페이지 × 페이지 크기 | " + " | ".join(f"{leaf[c] * d[c]['page_size'] / 1024:,.0f} KB" for c in C) + " |")
    for label, key in [("DATA_LENGTH (할당 기준)", "data_length"), ("INDEX_LENGTH (할당 기준)", "index_length"),
                       (".ibd 파일 크기 합", "ibd_bytes")]:
        cells = [f"{sum(x[key] for x in d[c]['tables'].values()) / 1024:,.0f} KB" for c in C]
        print(f"| {label} | " + " | ".join(cells) + " |")
    cells = [f"{sum(x['data_length'] + x['index_length'] for x in d[c]['tables'].values()) / 1024:,.0f} KB" for c in C]
    print("| DATA + INDEX | " + " | ".join(cells) + " |")
    print("| innodb_buffer_pool_size | " + " | ".join(f"{d[c]['buffer_pool_size'] / 1048576:.0f} MB" for c in C) + " |")


if __name__ == "__main__":
    for c in C:
        x = load("default")[c]
        print(c, x["version"], x["page_size"], x["buffer_pool_size"], x["flush_method"], x["load_at_startup"])
    for c in C:
        x = load("bp-min")[c]
        print(c, "bp-min", x["buffer_pool_size"], x["bp_pages"])
    warm_table(["default", "default-r2", "default-r3"], "기본 (buffer pool 128MB)")
    cold_table(["default", "default-r2", "default-r3"], "기본 (buffer pool 128MB)")
    size_table("default")
    bp_table("default", "기본 (buffer pool 128MB)")
    warm_table(["bp-min", "bp-min-r2", "bp-min-r3"], "buffer pool 9MB")
    cold_table(["bp-min", "bp-min-r2", "bp-min-r3"], "buffer pool 9MB")
    bp_table("bp-min", "buffer pool 9MB")
