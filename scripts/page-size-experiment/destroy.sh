#!/usr/bin/env bash
# 임시 컨테이너와 익명 볼륨을 지운다.
set -uo pipefail
source "$(dirname "$0")/env.sh"
for t in $TARGETS; do docker rm -f -v "${t%%:*}" >/dev/null 2>&1 && echo "삭제: ${t%%:*}"; done
docker ps -a --format '{{.Names}}' | grep -E '^ps-(8|16|32)k$' && echo "남은 컨테이너 있음" || echo "실험 컨테이너 없음"
