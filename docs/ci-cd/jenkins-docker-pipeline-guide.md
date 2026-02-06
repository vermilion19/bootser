# Jenkins + Docker CI/CD 파이프라인 가이드

모노레포 환경에서 서비스별로 독립적인 빌드/배포를 수행하는 Jenkins 파이프라인 구성 가이드입니다.

---

## 목차

1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [d-day-service (백엔드)](#d-day-service-백엔드)
4. [dday-web (프론트엔드)](#dday-web-프론트엔드)
5. [변경 감지 전략](#변경-감지-전략)
6. [Jenkins 설정 가이드](#jenkins-설정-가이드)
7. [트러블슈팅](#트러블슈팅)

---

## 개요

### 목적

- **모노레포 환경**에서 특정 서비스만 변경되었을 때 해당 서비스만 빌드/배포
- 불필요한 빌드 방지로 **CI/CD 리소스 절약**
- 서비스별 **독립적인 배포 사이클** 유지

### 적용 서비스

| 서비스 | 경로 | 역할 |
|--------|------|------|
| d-day-service | `apps/d-day/d-day-service/` | D-Day 백엔드 API (Spring Boot) |
| dday-web | `frontends/dday-web/` | D-Day 프론트엔드 (React + Vite) |

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                         GitHub Repository                        │
│                              (Push)                              │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Webhook
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Jenkins Controller                        │
│                                                                  │
│   ┌─────────────────────┐    ┌─────────────────────┐            │
│   │  d-day-service Job  │    │    dday-web Job     │            │
│   │                     │    │                     │            │
│   │  1. 변경 감지       │    │  1. 변경 감지       │            │
│   │  2. Gradle 빌드     │    │  2. Lint            │            │
│   │  3. 테스트          │    │  3. Docker 빌드     │            │
│   │  4. Docker 빌드     │    │  4. Push            │            │
│   │  5. Push            │    │  5. Deploy          │            │
│   │  6. Deploy          │    │                     │            │
│   └──────────┬──────────┘    └──────────┬──────────┘            │
└──────────────┼──────────────────────────┼───────────────────────┘
               │                          │
               ▼                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Docker Registry                            │
│                                                                  │
│   booster/d-day-service:latest    booster/dday-web:latest       │
│   booster/d-day-service:42        booster/dday-web:42           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Kubernetes Cluster                          │
│                                                                  │
│   ┌─────────────────────┐    ┌─────────────────────┐            │
│   │  d-day-service Pod  │    │   dday-web Pod      │            │
│   │  (Spring Boot)      │    │   (Nginx)           │            │
│   └─────────────────────┘    └─────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

---

## d-day-service (백엔드)

### 파일 구조

```
apps/d-day/d-day-service/
├── Jenkinsfile          # CI/CD 파이프라인 정의
├── Dockerfile           # Docker 이미지 빌드 설정
├── build.gradle         # Gradle 빌드 설정
└── src/
    └── main/java/...    # 소스 코드
```

### Jenkinsfile 상세 설명

```groovy
pipeline {
    agent any

    environment {
        SERVICE_NAME = 'd-day-service'           # 서비스 식별자
        SERVICE_PATH = 'apps/d-day/d-day-service' # 서비스 경로
        DOCKER_IMAGE = 'booster/d-day-service'   # Docker 이미지명
        GRADLE_OPTS = '-Dorg.gradle.daemon=false' # Gradle 데몬 비활성화 (CI 환경)
    }
```

#### Stage 1: Checkout

```groovy
stage('Checkout') {
    steps {
        checkout scm  # Git에서 소스 코드 체크아웃
    }
}
```

#### Stage 2: Check Changes (변경 감지)

```groovy
stage('Check Changes') {
    steps {
        script {
            # 마지막 성공 빌드와 현재 커밋 간의 변경된 파일 목록 추출
            def changes = sh(
                script: "git diff --name-only ${GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'} ${GIT_COMMIT}",
                returnStdout: true
            ).trim()

            # 빌드 필요 여부 판단
            def shouldBuild = changes.contains('apps/d-day/d-day-service') ||
                             changes.contains('libs/common') ||
                             changes.contains('libs/core-web') ||
                             changes.contains('libs/storage-db') ||
                             changes.contains('libs/storage-redis') ||
                             changes.contains('libs/core-resilience')

            if (!shouldBuild) {
                currentBuild.result = 'NOT_BUILT'
                # 빌드 스킵
            }
        }
    }
}
```

**감지 대상 경로:**

| 경로 | 빌드 이유 |
|------|-----------|
| `apps/d-day/d-day-service/**` | 서비스 코드 직접 변경 |
| `libs/common/**` | 공통 유틸리티 의존 |
| `libs/core-web/**` | 웹 공통 모듈 의존 |
| `libs/storage-db/**` | JPA 설정 의존 |
| `libs/storage-redis/**` | Redis 캐시 의존 |
| `libs/core-resilience/**` | Resilience4j 의존 |

#### Stage 3: Build (Gradle 빌드)

```groovy
stage('Build') {
    steps {
        sh './gradlew :apps:d-day:d-day-service:clean :apps:d-day:d-day-service:build -x test'
    }
}
```

- **`:apps:d-day:d-day-service:build`**: 해당 모듈만 빌드
- **`-x test`**: 테스트는 별도 스테이지에서 실행

#### Stage 4: Test

```groovy
stage('Test') {
    steps {
        sh './gradlew :apps:d-day:d-day-service:test'
    }
    post {
        always {
            junit '**/build/test-results/test/*.xml'  # 테스트 결과 리포트
        }
    }
}
```

#### Stage 5: Build Docker Image

```groovy
stage('Build Docker Image') {
    steps {
        dir("${SERVICE_PATH}") {
            sh """
                docker build \
                    -t ${DOCKER_IMAGE}:${BUILD_NUMBER} \
                    -t ${DOCKER_IMAGE}:latest \
                    .
            """
        }
    }
}
```

**이미지 태그 전략:**
- `${BUILD_NUMBER}`: 롤백 가능한 버전 관리
- `latest`: 최신 버전 참조

#### Stage 6-7: Push & Deploy

```groovy
stage('Push to Registry') {
    # Docker Registry에 이미지 푸시
}

stage('Deploy') {
    steps {
        sh """
            kubectl set image deployment/${SERVICE_NAME} \
                ${SERVICE_NAME}=${DOCKER_IMAGE}:${BUILD_NUMBER} \
                -n booster
        """
    }
}
```

### Dockerfile 상세 설명

```dockerfile
FROM eclipse-temurin:25-jre    # Java 25 런타임 (경량)

WORKDIR /app

ARG JAR_FILE=build/libs/*.jar  # Gradle이 생성한 JAR 파일
COPY ${JAR_FILE} app.jar       # JAR 복사

EXPOSE 8081                    # 포트 노출

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**특징:**
- **JRE 이미지 사용**: JDK 대비 ~200MB 절약
- **빌드 결과물만 복사**: 소스 코드 미포함으로 이미지 경량화
- **Jenkinsfile에서 빌드 후 Docker 빌드**: 빌드 캐싱 효율적

---

## dday-web (프론트엔드)

### 파일 구조

```
frontends/dday-web/
├── Jenkinsfile          # CI/CD 파이프라인 정의
├── Dockerfile           # Docker 이미지 빌드 설정 (멀티스테이지)
├── nginx.conf           # Nginx 웹서버 설정
├── package.json         # npm 의존성
└── src/
    └── ...              # React 소스 코드
```

### Jenkinsfile 상세 설명

```groovy
pipeline {
    agent any

    environment {
        SERVICE_NAME = 'dday-web'
        SERVICE_PATH = 'frontends/dday-web'
        DOCKER_IMAGE = 'booster/dday-web'
        NODE_VERSION = '22'
    }

    tools {
        nodejs "${NODE_VERSION}"  # NodeJS 플러그인 사용
    }
```

#### Stage 1-2: Checkout & Check Changes

백엔드와 동일한 방식으로 변경 감지:

```groovy
def shouldBuild = changes.contains('frontends/dday-web')
```

**프론트엔드는 libs 의존성이 없으므로 자체 경로만 감지**

#### Stage 3: Lint

```groovy
stage('Lint') {
    steps {
        dir("${SERVICE_PATH}") {
            sh 'npm ci'        # 의존성 설치
            sh 'npm run lint'  # ESLint 실행
        }
    }
}
```

**Lint만 수행하는 이유:**
- Dockerfile이 멀티스테이지 빌드로 `npm run build` 수행
- 중복 빌드 방지로 CI 시간 단축

#### Stage 4: Build Docker Image

```groovy
stage('Build Docker Image') {
    steps {
        dir("${SERVICE_PATH}") {
            sh """
                docker build \
                    -t ${DOCKER_IMAGE}:${BUILD_NUMBER} \
                    -t ${DOCKER_IMAGE}:latest \
                    .
            """
        }
    }
}
```

### Dockerfile 상세 설명 (멀티스테이지)

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder

WORKDIR /app

# 의존성 캐싱을 위해 package.json 먼저 복사
COPY package.json package-lock.json* ./
RUN npm ci

# 소스 복사 및 빌드
COPY . .
RUN npm run build   # TypeScript 컴파일 + Vite 빌드
```

```dockerfile
# Stage 2: Production
FROM nginx:alpine

# Nginx 설정 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 빌드된 정적 파일만 복사 (node_modules 제외)
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**멀티스테이지 빌드의 장점:**

| 비교 항목 | 싱글 스테이지 | 멀티스테이지 |
|-----------|---------------|--------------|
| 최종 이미지 크기 | ~1GB (Node.js 포함) | ~25MB (Nginx + 정적 파일) |
| 보안 | node_modules 노출 | 빌드 결과만 포함 |
| 빌드 시간 | - | 레이어 캐싱 활용 |

### nginx.conf 상세 설명

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # gzip 압축 (네트워크 전송량 감소)
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
    gzip_min_length 1000;

    # API 프록시 (CORS 우회)
    location /api/v1/ {
        proxy_pass http://gateway-service:6000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 정적 파일 캐싱 (1년)
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # SPA 라우팅 (React Router 지원)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 보안 헤더
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
}
```

**주요 설정:**

| 설정 | 역할 |
|------|------|
| `try_files ... /index.html` | SPA 클라이언트 라우팅 지원 |
| `proxy_pass` | 백엔드 API 프록시 (CORS 문제 해결) |
| `expires 1y` | 정적 파일 브라우저 캐싱 |
| `gzip on` | 응답 압축으로 전송 속도 향상 |

---

## 변경 감지 전략

### 동작 원리

```
마지막 성공 빌드 커밋 (GIT_PREVIOUS_SUCCESSFUL_COMMIT)
          │
          │  git diff --name-only
          ▼
현재 커밋 (GIT_COMMIT)
          │
          │  변경된 파일 목록
          ▼
┌─────────────────────────────────────┐
│  apps/d-day/d-day-service/App.java  │ → d-day-service 빌드
│  libs/common/Utils.java             │ → 모든 서비스 빌드
│  frontends/dday-web/App.tsx         │ → dday-web 빌드
│  docs/readme.md                     │ → 빌드 안 함
└─────────────────────────────────────┘
```

### 의존성 매핑 (d-day-service)

```
d-day-service
    │
    ├── libs/common           (공통 유틸)
    ├── libs/core-web         (REST 공통)
    ├── libs/storage-db       (JPA)
    ├── libs/storage-redis    (Redis)
    └── libs/core-resilience  (CircuitBreaker)
```

**이 중 하나라도 변경되면 d-day-service 빌드 트리거**

---

## Jenkins 설정 가이드

### 1. 필수 플러그인

| 플러그인 | 용도 |
|----------|------|
| Pipeline | Jenkinsfile 지원 |
| Git | Git SCM |
| GitHub | Webhook 연동 |
| NodeJS | npm 빌드 |
| Docker Pipeline | Docker 빌드 |
| Kubernetes | kubectl 배포 |

### 2. Credentials 설정

```
Jenkins 관리 → Credentials → Add Credentials

ID: docker-registry
Type: Username with password
Username: <registry 사용자명>
Password: <registry 비밀번호>
```

### 3. Multibranch Pipeline 생성

```
새 Item → Multibranch Pipeline

Branch Sources:
  - GitHub
  - Repository: https://github.com/your-org/booster

Build Configuration:
  - Mode: by Jenkinsfile
  - Script Path: apps/d-day/d-day-service/Jenkinsfile  (또는 frontends/dday-web/Jenkinsfile)
```

### 4. Webhook 설정 (GitHub)

```
Repository Settings → Webhooks → Add webhook

Payload URL: https://your-jenkins.com/github-webhook/
Content type: application/json
Events: Just the push event
```

---

## 트러블슈팅

### 1. 첫 빌드 시 GIT_PREVIOUS_SUCCESSFUL_COMMIT이 없는 경우

```groovy
${GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'}
```

Elvis 연산자로 fallback 처리

### 2. Gradle 데몬 충돌

```groovy
environment {
    GRADLE_OPTS = '-Dorg.gradle.daemon=false'
}
```

CI 환경에서는 데몬 비활성화 권장

### 3. Docker 레이어 캐시 활용

```dockerfile
# 의존성 먼저 복사 (변경 적음)
COPY package.json package-lock.json ./
RUN npm ci

# 소스는 나중에 복사 (변경 많음)
COPY . .
```

자주 변경되지 않는 레이어를 먼저 배치

### 4. libs 변경 시 영향받는 서비스 파악

```bash
# 어떤 서비스가 특정 lib을 의존하는지 확인
./gradlew :apps:d-day:d-day-service:dependencies | grep storage-redis
```

---

## 참고

- [Jenkinsfile 공식 문서](https://www.jenkins.io/doc/book/pipeline/jenkinsfile/)
- [Docker 멀티스테이지 빌드](https://docs.docker.com/develop/develop-images/multistage-build/)
- [Nginx 설정 가이드](https://nginx.org/en/docs/)
