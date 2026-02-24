#!/bin/bash
# ─────────────────────────────────────────────────────────────
# Booster Docker 실행 스크립트
# 사용법: ./dockers/up.sh [서비스] [서비스] ...
#   ./dockers/up.sh              → 전체 실행
#   ./dockers/up.sh infra        → infrastructure만
#   ./dockers/up.sh dday-web     → dday-web만
#   ./dockers/up.sh obs          → observability만
#   ./dockers/up.sh dday-back    → dday-backend만
# ─────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 네트워크 생성 (없으면)
docker network create booster-network 2>/dev/null || true

up() {
  local dir="$1"
  echo "▶ Starting $dir ..."
  docker compose -f "$SCRIPT_DIR/$dir/docker-compose.yml" up -d --build --remove-orphans
}

TARGETS=("$@")

# 인자 없으면 전체 실행
if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=("infrastructure" "observability" "dday-backend" "dday-web")
fi

for target in "${TARGETS[@]}"; do
  case "$target" in
    infra|infrastructure) up "infrastructure" ;;
    obs|observability)    up "observability" ;;
    dday-web)              up "dday-web" ;;
    dday-back|dday-backend) up "dday-backend" ;;
    *)
      echo "Unknown target: $target"
      echo "Available: infrastructure (infra), observability (obs), dday-web, dday-backend (dday-back)"
      exit 1
      ;;
  esac
done

echo "✅ Done."
