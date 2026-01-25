# WebFlux 모니터링 가이드

Docker 컨테이너의 CPU, 메모리, 네트워크 사용량을 모니터링하는 방법입니다.

## docker stats 명령어

### 기본 사용법

```bash
# 전체 컨테이너 실시간 모니터링 (Ctrl+C로 종료)
docker stats

# 스냅샷 (1회 출력)
docker stats --no-stream
```

### 포맷 지정

```bash
# 테이블 형식
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"

# webflux 관련 컨테이너만
docker stats $(docker ps --format '{{.Names}}' | grep -E 'webflux|chatting|attacker')
```

### 출력 필드

| 필드 | 설명 |
|------|------|
| `{{.Name}}` | 컨테이너 이름 |
| `{{.CPUPerc}}` | CPU 사용률 (%) |
| `{{.MemUsage}}` | 메모리 사용량 / 제한 |
| `{{.MemPerc}}` | 메모리 사용률 (%) |
| `{{.NetIO}}` | 네트워크 I/O (수신 / 송신) |
| `{{.BlockIO}}` | 블록 I/O (읽기 / 쓰기) |
| `{{.PIDs}}` | 프로세스 수 |

## 예시 출력

```
NAME                   CPU %     MEM USAGE / LIMIT    NET I/O
chatting-service       0.22%     412MB / 19.5GB       80.8MB / 63.6MB
webflux-attacker-1     0.16%     638MB / 2GB          16MB / 20.2MB
webflux-attacker-2     0.19%     654MB / 2GB          15.9MB / 20.2MB
webflux-attacker-3     0.12%     652MB / 2GB          15.9MB / 20.1MB
webflux-attacker-4     0.16%     636MB / 2GB          15.9MB / 20.2MB
webflux-redis          0.71%     19.5MB / 19.5GB      3.9KB / 1.1KB
logstream-service      0.19%     283MB / 19.5GB       2.6KB / 126B
```

## 유용한 스크립트

### 특정 컨테이너 모니터링

```bash
docker stats chatting-service webflux-attacker-1 webflux-attacker-2
```

### 메모리 사용량 높은 순 정렬

```bash
docker stats --no-stream --format "{{.Name}}\t{{.MemUsage}}" | sort -k2 -h -r
```

### 주기적 로깅

```bash
# 5초마다 stats.log에 기록
while true; do
  docker stats --no-stream --format "$(date +%T),{{.Name}},{{.CPUPerc}},{{.MemUsage}}" >> stats.log
  sleep 5
done
```
