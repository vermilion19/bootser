@echo off
chcp 65001 > nul
REM D-Day 서비스 배포 스크립트
REM 사용법: deploy.bat [서비스명]
REM   deploy.bat              - 전체 재빌드 + 재시작
REM   deploy.bat backend      - d-day-service만 재빌드
REM   deploy.bat frontend     - dday-web만 재빌드
REM   deploy.bat --no-cache   - 캐시 없이 전체 재빌드

cd /d "%~dp0"

echo ========================================
echo  D-Day 배포 스크립트
echo ========================================

REM 네트워크 생성 (없으면)
docker network create booster-network 2>nul

if "%1"=="backend" goto backend
if "%1"=="service" goto backend
if "%1"=="api" goto backend
if "%1"=="frontend" goto frontend
if "%1"=="web" goto frontend
if "%1"=="--no-cache" goto nocache
goto all

:backend
echo [1/2] d-day-service 빌드 중...
docker-compose build dday-service
echo [2/2] d-day-service 재시작 중...
docker-compose up -d dday-service
echo 완료! d-day-service가 재시작되었습니다.
goto status

:frontend
echo [1/2] dday-web 빌드 중...
docker-compose build dday-web
echo [2/2] dday-web 재시작 중...
docker-compose up -d dday-web
echo 완료! dday-web이 재시작되었습니다.
goto status

:nocache
echo [1/2] 전체 서비스 빌드 중 (캐시 무시)...
docker-compose build --no-cache
echo [2/2] 전체 서비스 재시작 중...
docker-compose up -d
echo 완료! 전체 서비스가 재시작되었습니다.
goto status

:all
echo [1/2] 변경된 서비스 빌드 중...
docker-compose build
echo [2/2] 서비스 재시작 중...
docker-compose up -d
echo 완료! 전체 서비스가 재시작되었습니다.
goto status

:status
echo.
echo ========================================
echo  서비스 상태
echo ========================================
docker-compose ps
