# Booster Frontend

React + TypeScript + Vite 기반 프론트엔드 프로젝트입니다.

## 사전 준비

### Node.js 설치 확인
```bash
node -v   # v20 이상 권장
npm -v    # v10 이상 권장
```

Node.js가 없다면: https://nodejs.org/ 에서 LTS 버전 설치

---

## 실행 방법

### 방법 1: 터미널에서 실행

```bash
# 1. frontend 폴더로 이동
cd frontend

# 2. 의존성 설치 (최초 1회 또는 package.json 변경 시)
npm install

# 3. 개발 서버 실행
npm run dev
```

브라우저에서 http://localhost:3000 접속

### 방법 2: IntelliJ에서 실행

1. **npm 탭 열기**
   - 우측 사이드바 → `npm` 탭 클릭
   - 또는 상단 메뉴 → `View` → `Tool Windows` → `npm`

2. **스크립트 실행**
   - `package.json` → `Scripts` → `dev` 더블클릭

3. **또는 package.json에서 직접 실행**
   - `frontend/package.json` 파일 열기
   - `"dev"` 옆에 있는 ▶ 재생 버튼 클릭

---

## 개발 서버 중지

- 터미널: `Ctrl + C`
- IntelliJ: 하단 Run 탭에서 빨간 정지 버튼 클릭

---

## 사용 가능한 명령어

| 명령어 | 설명 |
|--------|------|
| `npm run dev` | 개발 서버 실행 (http://localhost:3000) |
| `npm run build` | 프로덕션 빌드 (dist 폴더 생성) |
| `npm run preview` | 빌드 결과물 미리보기 |
| `npm run lint` | ESLint 코드 검사 |

---

## API 호출 방법

프록시가 설정되어 있어서 `/api`로 시작하면 자동으로 Gateway(localhost:6000)로 전달됩니다.

### 예시

```typescript
// 식당 목록 조회
const response = await fetch('/api/restaurants/v1');
const data = await response.json();

// 대기 등록
const response = await fetch('/api/waitings/v1', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    restaurantId: 1,
    userId: 1,
    partySize: 4,
    phoneNumber: '010-1234-5678'
  })
});
```

### 프록시 매핑

| 프론트엔드 요청 | 실제 요청 |
|----------------|----------|
| `/api/restaurants/v1` | `http://localhost:6000/restaurants/v1` |
| `/api/waitings/v1` | `http://localhost:6000/waitings/v1` |
| `/api/auth/v1/login` | `http://localhost:6000/auth/v1/login` |
| `/api/coupons/v1` | `http://localhost:6000/coupons/v1` |

---

## 프로젝트 구조

```
frontend/
├── public/             # 정적 파일
├── src/
│   ├── assets/         # 이미지, 폰트 등
│   ├── App.tsx         # 메인 컴포넌트
│   ├── App.css         # 메인 스타일
│   ├── main.tsx        # 엔트리 포인트
│   └── index.css       # 글로벌 스타일
├── index.html          # HTML 템플릿
├── package.json        # 의존성 및 스크립트
├── vite.config.ts      # Vite 설정 (프록시 포함)
├── tsconfig.json       # TypeScript 설정
└── eslint.config.js    # ESLint 설정
```

---

## 자주 발생하는 문제

### 1. `npm run dev` 실행 안됨
```bash
# node_modules 삭제 후 재설치
rm -rf node_modules
npm install
```

### 2. 포트 3000 이미 사용 중
```bash
# 다른 포트로 실행
npm run dev -- --port 3001
```

### 3. API 호출 시 CORS 에러
- Gateway(localhost:6000)가 실행 중인지 확인
- `/api`로 시작하는지 확인 (프록시 설정)

### 4. 변경사항이 반영 안됨
- 브라우저 캐시 삭제: `Ctrl + Shift + R` (강력 새로고침)
- 개발 서버 재시작

---

## 백엔드 연동 체크리스트

프론트엔드 개발 전 백엔드가 실행 중인지 확인:

```bash
# Docker로 전체 실행
cd dockers/services
docker compose up -d

# 또는 인프라만 실행
docker compose -f docker-compose-infra.yml up -d
```

### 서비스 상태 확인

| 서비스 | URL | 확인 방법 |
|--------|-----|----------|
| Gateway | http://localhost:6000/actuator/health | `{"status":"UP"}` |
| Eureka | http://localhost:8761 | 대시보드 표시 |
| Kafka UI | http://localhost:8989 | 대시보드 표시 |
