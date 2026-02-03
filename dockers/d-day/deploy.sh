#!/bin/bash
# D-Day 서비스 배포 스크립트
# 사용법: ./deploy.sh [서비스명] [옵션]
#   ./deploy.sh              # 전체 재빌드 + 재시작
#   ./deploy.sh backend      # d-day-service만 재빌드
#   ./deploy.sh frontend     # dday-web만 재빌드
#   ./deploy.sh --no-cache   # 캐시 없이 전체 재빌드

set -e

cd "$(dirname "$0")"

SERVICE=$1
OPTION=$2

echo "========================================"
echo " D-Day 배포 스크립트"
echo "========================================"

# 네트워크 생성 (없으면)
docker network create booster-network 2>/dev/null || true

case "$SERVICE" in
  backend|service|api)
    echo "[1/2] d-day-service 빌드 중..."
    docker-compose build dday-service
    echo "[2/2] d-day-service 재시작 중..."
    docker-compose up -d dday-service
    echo "완료! d-day-service가 재시작되었습니다."
    ;;
  frontend|web)
    echo "[1/2] dday-web 빌드 중..."
    docker-compose build dday-web
    echo "[2/2] dday-web 재시작 중..."
    docker-compose up -d dday-web
    echo "완료! dday-web이 재시작되었습니다."
    ;;
  --no-cache)
    echo "[1/2] 전체 서비스 빌드 중 (캐시 무시)..."
    docker-compose build --no-cache
    echo "[2/2] 전체 서비스 재시작 중..."
    docker-compose up -d
    echo "완료! 전체 서비스가 재시작되었습니다."
    ;;
  *)
    echo "[1/2] 변경된 서비스 빌드 중..."
    docker-compose build
    echo "[2/2] 서비스 재시작 중..."
    docker-compose up -d
    echo "완료! 전체 서비스가 재시작되었습니다."
    ;;
esac

echo ""
echo "========================================"
echo " 서비스 상태"
echo "========================================"
docker-compose ps
