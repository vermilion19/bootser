# Damagochi Web

## 실행
```bash
npm install -w @booster/damagochi-web
npm run dev -w @booster/damagochi-web
```

## 환경변수
- `VITE_API_BASE_URL`
  - 기본값: `http://localhost:8080`
  - 예: `http://localhost:18080`

예시 (`frontends/damagochi-web/.env.local`):
```env
VITE_API_BASE_URL=http://localhost:18080
```

## 주요 라우트
- `/login`
- `/signup`
- `/app`
- `/app/creatures`
- `/app/creatures/new`
- `/app/battle`
- `/app/battle/room`
- `/app/battle/:battleId`
- `/app/settings`
