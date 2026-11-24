# 페이지 크기 실험 공통 설정. 같은 폴더의 bash 스크립트가 source 한다 (zsh에서 직접 source하면 경로가 현재 폴더로 잡힌다).
# 기존 로컬 DB(riftdata-mysql)와 배포 서버는 건드리지 않고, 아래 임시 컨테이너만 만들고 지운다.

IMAGE=mysql:8.0
ROOT_PW=pagesize-exp          # 임시 컨테이너 전용 비밀번호 (실험 후 컨테이너와 함께 삭제)
DB=riftdata
SOURCE_CONTAINER=riftdata-mysql   # 데이터를 읽어 올 기존 로컬 DB (읽기만 한다)
SOURCE_PW=${SOURCE_PW:-root}

# 이름:페이지크기:포트
TARGETS="ps-8k:8k:3309 ps-16k:16k:3310 ps-32k:32k:3308"   # 16k는 3307이 다른 컨테이너와 겹쳐 3310

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="$HERE/work"          # 덤프·측정 결과 (커밋하지 않음)
mkdir -p "$WORK"

q() {  # q <컨테이너> <SQL> : 결과를 탭 구분으로
  docker exec -i "$1" mysql -uroot -p"$ROOT_PW" -N -B --default-character-set=utf8mb4 "$DB" -e "$2" 2>/dev/null
}

wait_ready() {
  for _ in $(seq 1 90); do
    docker exec "$1" mysql -uroot -p"$ROOT_PW" -e "SELECT 1" >/dev/null 2>&1 && return 0
    sleep 2
  done
  echo "$1 이 준비되지 않음" >&2; return 1
}
