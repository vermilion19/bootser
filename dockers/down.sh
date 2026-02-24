#!/bin/bash
# ─────────────────────────────────────────────────────────────
# Booster Docker 중지 스크립트
# 사용법: ./dockers/down.sh [서비스] [서비스] ...
#   ./dockers/down.sh              → 전체 중지
#   ./dockers/down.sh infra        → infrastructure만
# ─────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

down() {
  local dir="$1"
  echo "▶ Stopping $dir ..."
  docker compose -f "$SCRIPT_DIR/$dir/docker-compose.yml" down
}

TARGETS=("$@")

if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=("dday-web" "dday-backend" "observability" "infrastructure")
fi

for target in "${TARGETS[@]}"; do
  case "$target" in
    infra|infrastructure) down "infrastructure" ;;
    obs|observability)    down "observability" ;;
    dday-web)              down "dday-web" ;;
    dday-back|dday-backend) down "dday-backend" ;;
    *)
      echo "Unknown target: $target"
      exit 1
      ;;
  esac
done

echo "✅ Done."
