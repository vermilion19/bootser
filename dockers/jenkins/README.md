# Jenkins CI/CD 설정 가이드

## 개요
Docker 기반 Jenkins를 사용하여 Booster 서비스들을 빌드/배포하는 가이드입니다.

## 사전 준비

### 1. Docker Network 생성 (최초 1회)
```bash
docker network create booster-network
```

### 2. Docker CLI 설치 (최초 1회)
Jenkins 컨테이너 내에서 Docker 명령을 사용하기 위해 필요합니다.
```bash
cd dockers/jenkins
docker compose --profile setup up jenkins-docker-setup
```

## Jenkins 실행

### 시작
```bash
cd dockers/jenkins
docker compose up -d
```

### 중지
```bash
cd dockers/jenkins
docker compose down
```

### 로그 확인
```bash
docker logs -f booster-jenkins
```

## 초기 설정

### 1. 초기 비밀번호 확인
```bash
docker exec booster-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 2. Jenkins 접속
- URL: http://localhost:9090
- 초기 비밀번호 입력

### 3. 플러그인 설치
- "Install suggested plugins" 선택
- 추가로 필요한 플러그인:
  - Docker Pipeline
  - Pipeline Utility Steps

### 4. 관리자 계정 생성
- 사용자명, 비밀번호, 이름, 이메일 입력

## Pipeline Job 생성

### 1. 새 Item 생성
1. Jenkins 대시보드 → "새 Item"
2. 이름 입력 (예: `booster-deploy`)
3. "Pipeline" 선택 → OK

### 2. Pipeline 설정
1. "Pipeline" 섹션으로 이동
2. Definition: `Pipeline script from SCM`
3. SCM: `Git`
4. Repository URL: `/workspace/bootser` (로컬) 또는 Git 저장소 URL
5. Branch: `*/main`
6. Script Path: `Jenkinsfile`
7. 저장

## 빌드 실행

### 파라미터 설명

| 파라미터 | 옵션 | 설명 |
|---------|------|------|
| DEPLOY_TARGET | all | 모든 서비스 배포 |
| | waiting-service | 웨이팅 서비스만 |
| | restaurant-service | 식당 서비스만 |
| | auth-service | 인증 서비스만 |
| | notification-service | 알림 서비스만 |
| | promotion-service | 프로모션 서비스만 |
| | gateway-service | 게이트웨이만 |
| | discovery-service | 디스커버리만 |
| ACTION | deploy | 빌드 후 배포 |
| | restart | 컨테이너 재시작 |
| | stop | 컨테이너 중지 |
| | logs | 로그 조회 |
| REBUILD_IMAGE | true/false | 이미지 재빌드 여부 |

### 실행 방법
1. Job 선택 → "Build with Parameters"
2. 원하는 서비스와 액션 선택
3. "빌드하기" 클릭

## 사용 예시

### 전체 서비스 배포
- DEPLOY_TARGET: `all`
- ACTION: `deploy`
- REBUILD_IMAGE: `true`

### 특정 서비스만 재시작
- DEPLOY_TARGET: `waiting-service`
- ACTION: `restart`

### 특정 서비스 로그 확인
- DEPLOY_TARGET: `restaurant-service`
- ACTION: `logs`

### 이미지 재빌드 없이 배포
- DEPLOY_TARGET: `auth-service`
- ACTION: `deploy`
- REBUILD_IMAGE: `false`

## 포트 정보

| 서비스 | 포트 |
|--------|------|
| Jenkins | 9090 |
| Jenkins Agent | 50000 |

## 트러블슈팅

### Docker 명령 실행 안됨
```bash
# Jenkins 컨테이너에서 Docker 권한 확인
docker exec -it booster-jenkins ls -la /var/run/docker.sock
```

### 빌드 실패 시
1. Jenkins 콘솔 출력 확인
2. Gradle 빌드 로그 확인
3. Docker 이미지 빌드 로그 확인

### 컨테이너 상태 확인
```bash
docker compose -f dockers/services/docker-compose.yml ps
```