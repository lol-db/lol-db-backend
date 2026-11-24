#!/usr/bin/env bash
# 실험 전체를 처음부터 다시 돌린다: 생성 → 적재 → 측정(3회) → buffer pool 최소로 다시 → 표 출력 → 삭제
# 결과 JSON은 work/에 남는다. 한 번에 약 15분.
set -euo pipefail
cd "$(dirname "$0")"
./create.sh 128M && ./load.sh
for r in default default-r2 default-r3; do python3 -I measure.py "$r"; done
python3 -I measure.py sizes-only
./create.sh 9M && ./load.sh
for r in bp-min bp-min-r2 bp-min-r3; do python3 -I measure.py "$r"; done
python3 -I report.py > work/report.md
./destroy.sh
echo "표: work/report.md"
