#!/bin/bash
# MySQL 컨테이너를 처음 만들 때(빈 볼륨) 한 번 실행된다. 01-schema.sql 다음.
# 서버용 조회 전용 계정을 만든다 (sql/demo/01_users_grants.sql의 riftdata_app과 같은 권한).
# 쓰기 계정(DB_USERNAME)은 MySQL 이미지가 MYSQL_USER로 만들고 riftdata DB 전체 권한을 준다 (CSV 적재용).
set -euo pipefail

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${DB_APP_USERNAME}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
GRANT SELECT ON \`${MYSQL_DATABASE}\`.* TO '${DB_APP_USERNAME}'@'%';
SQL
echo "[02-app-user] ${DB_APP_USERNAME}: SELECT ON ${MYSQL_DATABASE}.*"
