#!/usr/bin/env bash
# 임시 컨테이너 3개를 만들고 schema.sql을 적용한다.
#   ./create.sh            buffer pool 기본값(128MB)
#   ./create.sh 9M         buffer pool 최소 시나리오 (32k는 최소 8,519,680바이트라 5M이면 시작 실패, 셋 다 되는 9M로 통일)
# innodb_page_size는 데이터 디렉터리를 처음 만들 때만 정할 수 있으므로 볼륨 없이 새로 만든다.
set -euo pipefail
source "$(dirname "$0")/env.sh"
BP=${1:-128M}

for t in $TARGETS; do
  IFS=: read -r name page port <<<"$t"
  docker rm -f -v "$name" >/dev/null 2>&1 || true
  docker run -d --name "$name" -p "127.0.0.1:$port:3306" \
    -e MYSQL_ROOT_PASSWORD="$ROOT_PW" -e MYSQL_DATABASE="$DB" -e TZ=Asia/Seoul \
    "$IMAGE" \
    --innodb-page-size="$page" \
    --innodb-buffer-pool-size="$BP" \
    --innodb-buffer-pool-load-at-startup=OFF \
    --innodb-buffer-pool-dump-at-shutdown=OFF \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
done
for t in $TARGETS; do
  IFS=: read -r name page port <<<"$t"
  wait_ready "$name"
  docker exec -i "$name" mysql -uroot -p"$ROOT_PW" "$DB" < "$ROOT/src/main/resources/db/schema.sql" 2>/dev/null
  echo "$name: $(q "$name" "SELECT CONCAT('page=', @@innodb_page_size, ' buffer_pool=', @@innodb_buffer_pool_size, ' version=', VERSION())")"
done
