# Stage 1: Build
FROM node:22-alpine AS builder

WORKDIR /app

# 의존성 설치 (캐시 활용)
COPY package.json package-lock.json* ./
RUN npm ci

# 소스 복사 및 빌드
COPY . .
RUN npm run build

# Stage 2: Production (Nginx)
FROM nginx:alpine

# nginx 설정 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 빌드된 정적 파일 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# 80 포트 노출
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
