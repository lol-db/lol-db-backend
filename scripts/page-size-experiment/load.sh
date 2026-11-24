#!/usr/bin/env bash
# 기존 로컬 DB에서 데이터만 덤프(읽기만)해 세 컨테이너에 넣고, 테이블별 행 수가 같은지 확인한다.
set -euo pipefail
source "$(dirname "$0")/env.sh"
TABLES="team player champion stage lck_match match_set team_set player_set ban player_season"
DUMP="$WORK/data.sql"

if [ ! -s "$DUMP" ]; then
  docker exec "$SOURCE_CONTAINER" mysqldump -uroot -p"$SOURCE_PW" --single-transaction --no-create-info \
    --skip-triggers --complete-insert --default-character-set=utf8mb4 "$DB" $TABLES > "$DUMP" 2>/dev/null
fi

for t in $TARGETS; do
  IFS=: read -r name page port <<<"$t"
  docker exec -i "$name" mysql -uroot -p"$ROOT_PW" --default-character-set=utf8mb4 "$DB" < "$DUMP" 2>/dev/null
done

# 행 수 비교 (원본 포함)
printf "%-14s %8s" table source; for t in $TARGETS; do printf " %8s" "${t%%:*}"; done; echo
ok=1
for tb in $TABLES; do
  src=$(docker exec "$SOURCE_CONTAINER" mysql -uroot -p"$SOURCE_PW" -N -e "SELECT COUNT(*) FROM $DB.$tb" 2>/dev/null)
  printf "%-14s %8s" "$tb" "$src"
  for t in $TARGETS; do
    n=$(q "${t%%:*}" "SELECT COUNT(*) FROM $tb"); printf " %8s" "$n"
    [ "$n" = "$src" ] || ok=0
  done; echo
done
[ $ok = 1 ] && echo "행 수 모두 같음" || { echo "행 수가 다름" >&2; exit 1; }
