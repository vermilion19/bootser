# 네트워크 면접 핵심 정리

## 1. OSI 7계층 vs TCP/IP 4계층

### OSI 7계층

| 계층 | 이름 | 역할 | 프로토콜/장비 |
|------|------|------|---------------|
| 7 | Application | 사용자 인터페이스, 앱 서비스 | HTTP, FTP, SMTP, DNS |
| 6 | Presentation | 데이터 변환, 암호화, 압축 | SSL/TLS, JPEG, ASCII |
| 5 | Session | 세션 관리, 동기화 | NetBIOS, RPC |
| 4 | Transport | 신뢰성 있는 데이터 전송 | TCP, UDP |
| 3 | Network | 라우팅, 논리적 주소(IP) | IP, ICMP, ARP / 라우터 |
| 2 | Data Link | 프레임 단위 전송, MAC 주소 | Ethernet, PPP / 스위치 |
| 1 | Physical | 비트 전송, 전기 신호 | 케이블, 허브 |

### TCP/IP 4계층

| 계층 | OSI 매핑 | 역할 |
|------|----------|------|
| Application | 5~7 | HTTP, DNS, FTP 등 |
| Transport | 4 | TCP, UDP |
| Internet | 3 | IP, ICMP, ARP |
| Network Access | 1~2 | Ethernet, Wi-Fi |

> **면접 포인트**: "왜 OSI는 7계층인데 실제로는 TCP/IP 4계층을 쓰나요?"
> → OSI는 이론적 참조 모델이고, TCP/IP는 실제 구현 기반의 프로토콜 스택이다. OSI는 계층 분리가 엄격하지만 실무에서는 TCP/IP처럼 단순화된 모델이 더 실용적이다.

---

## 2. TCP vs UDP

### TCP (Transmission Control Protocol)

- **연결 지향**: 3-way handshake로 연결 수립
- **신뢰성 보장**: 순서 보장, 재전송, 흐름/혼잡 제어
- **사용처**: HTTP, HTTPS, FTP, SSH, 이메일

### UDP (User Datagram Protocol)

- **비연결**: handshake 없음
- **신뢰성 없음**: 순서 보장 X, 재전송 X
- **빠름**: 오버헤드가 적음
- **사용처**: DNS, 스트리밍, 온라인 게임, VoIP

### 비교표

| 항목 | TCP | UDP |
|------|-----|-----|
| 연결 방식 | 연결 지향 | 비연결 |
| 신뢰성 | 보장 | 미보장 |
| 순서 보장 | O | X |
| 속도 | 상대적 느림 | 빠름 |
| 헤더 크기 | 20바이트 | 8바이트 |
| 흐름/혼잡 제어 | O | X |

---

## 3. TCP 3-way Handshake / 4-way Handshake

### 3-way Handshake (연결 수립)

```
Client              Server
  |--- SYN ----------->|    1) 클라이언트가 SYN(seq=x) 전송
  |<-- SYN+ACK --------|    2) 서버가 SYN(seq=y)+ACK(ack=x+1) 응답
  |--- ACK ----------->|    3) 클라이언트가 ACK(ack=y+1) 전송
  |    [연결 수립 완료]     |
```

> **왜 3-way인가?** → 양쪽 모두 송수신 가능함을 확인해야 하므로 최소 3번의 교환이 필요하다. 2-way로는 서버가 클라이언트의 수신 능력을 확인할 수 없다.

### 4-way Handshake (연결 종료)

```
Client              Server
  |--- FIN ----------->|    1) 클라이언트가 FIN 전송 (종료 요청)
  |<-- ACK ------------|    2) 서버가 ACK 응답 (확인)
  |<-- FIN ------------|    3) 서버가 FIN 전송 (서버도 종료 준비 완료)
  |--- ACK ----------->|    4) 클라이언트가 ACK 응답
  |  [TIME_WAIT 진입]      |
```

> **TIME_WAIT는 왜 필요한가?**
> → 마지막 ACK가 유실될 경우 서버가 FIN을 재전송할 수 있어야 하므로, 클라이언트는 일정 시간(2MSL) 대기한다. 또한 이전 연결의 지연 패킷이 새 연결에 영향을 주는 것을 방지한다.

---

## 4. HTTP/HTTPS

### HTTP 메서드

| 메서드 | 멱등성 | 안전성 | 용도 |
|--------|--------|--------|------|
| GET | O | O | 리소스 조회 |
| POST | X | X | 리소스 생성 |
| PUT | O | X | 리소스 전체 교체 |
| PATCH | X | X | 리소스 부분 수정 |
| DELETE | O | X | 리소스 삭제 |
| HEAD | O | O | 헤더만 조회 |
| OPTIONS | O | O | 지원 메서드 확인 |

> **멱등성(Idempotent)**: 같은 요청을 여러 번 보내도 결과가 동일한 성질. PUT은 멱등이지만 PATCH는 구현에 따라 다를 수 있다.

### HTTP 상태 코드

| 범위 | 의미 | 대표 코드 |
|------|------|-----------|
| 1xx | 정보 | 100 Continue |
| 2xx | 성공 | 200 OK, 201 Created, 204 No Content |
| 3xx | 리다이렉트 | 301 Moved Permanently, 302 Found, 304 Not Modified |
| 4xx | 클라이언트 오류 | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found |
| 5xx | 서버 오류 | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable |

> **401 vs 403**: 401은 인증(Authentication) 실패, 403은 인가(Authorization) 실패. 401은 "너 누구야?", 403은 "너인 건 아는데 권한이 없어."

### HTTP/1.1 vs HTTP/2 vs HTTP/3

| 항목 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|------|----------|--------|--------|
| 전송 | 텍스트 기반 | 바이너리 프레이밍 | 바이너리 프레이밍 |
| 다중화 | X (파이프라이닝 제한적) | O (멀티플렉싱) | O |
| HOL Blocking | 있음 | TCP 레벨에서 있음 | 없음 (QUIC) |
| 전송 계층 | TCP | TCP | UDP (QUIC) |
| 헤더 압축 | X | HPACK | QPACK |
| 서버 푸시 | X | O | O |

### HTTPS (TLS Handshake 요약)

```
1. Client Hello: 지원하는 암호화 방식, 랜덤값 전송
2. Server Hello: 선택된 암호화 방식, 인증서, 랜덤값 전송
3. 클라이언트가 인증서 검증 (CA 체인 확인)
4. Pre-Master Secret 교환 (비대칭 암호화)
5. 양쪽이 Session Key 생성 (대칭키)
6. 이후 통신은 대칭키로 암호화
```

> **왜 대칭키와 비대칭키를 같이 쓰나?** → 비대칭키는 안전하지만 느리고, 대칭키는 빠르지만 키 교환이 위험하다. 따라서 비대칭키로 대칭키를 안전하게 교환한 후, 이후 통신은 대칭키로 수행한다.

---

## 5. DNS (Domain Name System)

### DNS 질의 과정

```
1. 브라우저 캐시 확인
2. OS 캐시 확인 (/etc/hosts)
3. Local DNS 서버 (ISP의 Recursive Resolver)
4. Root DNS 서버 → ".com" 담당 TLD 서버 주소 반환
5. TLD DNS 서버 → "example.com" 담당 Authoritative 서버 주소 반환
6. Authoritative DNS 서버 → 최종 IP 주소 반환
7. Local DNS가 결과를 캐시하고 클라이언트에 응답
```

### DNS 레코드 타입

| 타입 | 설명 |
|------|------|
| A | 도메인 → IPv4 주소 |
| AAAA | 도메인 → IPv6 주소 |
| CNAME | 도메인 → 다른 도메인 (별칭) |
| MX | 메일 서버 지정 |
| NS | 네임서버 지정 |
| TXT | 텍스트 정보 (SPF, DKIM 등) |
| SRV | 서비스 위치 (호스트+포트) |

---

## 6. 웹 브라우저에 URL을 입력하면 일어나는 일

이 질문은 네트워크 면접의 **단골 질문**이다.

```
1. URL 파싱 (프로토콜, 도메인, 포트, 경로 분리)
2. DNS 조회 → 도메인을 IP 주소로 변환
3. TCP 3-way Handshake로 서버와 연결 수립
4. (HTTPS라면) TLS Handshake로 암호화 채널 수립
5. HTTP 요청 전송 (GET / HTTP/1.1 ...)
6. 서버가 요청 처리 후 HTTP 응답 반환 (HTML, JSON 등)
7. 브라우저가 HTML 파싱 → DOM 트리 구축
8. CSS 파싱 → CSSOM 트리 구축
9. DOM + CSSOM → Render Tree 구축
10. Layout (Reflow): 각 노드의 위치/크기 계산
11. Paint: 픽셀로 변환하여 화면에 그림
12. Composite: 레이어 합성 후 최종 출력
```

---

## 7. 쿠키 vs 세션 vs 토큰 (JWT)

| 항목 | 쿠키 | 세션 | JWT |
|------|------|------|-----|
| 저장 위치 | 클라이언트 (브라우저) | 서버 메모리/DB | 클라이언트 |
| 전송 방식 | 자동 (Cookie 헤더) | Session ID를 쿠키로 전송 | Authorization 헤더 |
| 서버 부하 | 낮음 | 높음 (상태 유지) | 낮음 (Stateless) |
| 확장성 | 보통 | 낮음 (Sticky Session 필요) | 높음 (Scale-out 용이) |
| 보안 | XSS, CSRF 취약 | 서버에서 관리하므로 상대적 안전 | 토큰 탈취 시 만료까지 유효 |
| 무효화 | 쿠키 삭제 | 서버에서 세션 삭제 | 어려움 (블랙리스트 필요) |

> **Scale-out 환경에서 세션 관리**: 서버가 여러 대일 때 세션을 공유해야 한다. 방법으로는 Sticky Session, Session Clustering, Redis 같은 외부 저장소 사용이 있으며, JWT를 쓰면 서버가 상태를 갖지 않으므로 가장 확장에 유리하다.

---

## 8. REST API 설계 원칙

### RESTful API의 6가지 제약 조건

1. **Client-Server**: 클라이언트와 서버의 관심사 분리
2. **Stateless**: 각 요청은 독립적, 서버가 클라이언트 상태를 저장하지 않음
3. **Cacheable**: 응답에 캐시 가능 여부를 명시
4. **Uniform Interface**: 일관된 인터페이스 (URI로 리소스 식별)
5. **Layered System**: 클라이언트는 중간 계층(프록시, 게이트웨이)의 존재를 모름
6. **Code on Demand (선택)**: 서버가 클라이언트에 실행 코드를 전송

### URI 설계 Best Practice

```
# 좋은 예
GET    /api/v1/users          # 목록 조회
GET    /api/v1/users/123      # 단건 조회
POST   /api/v1/users          # 생성
PUT    /api/v1/users/123      # 전체 수정
PATCH  /api/v1/users/123      # 부분 수정
DELETE /api/v1/users/123      # 삭제

# 나쁜 예
GET /api/getUser              # 동사 사용
POST /api/deleteUser/123      # HTTP 메서드와 불일치
GET /api/user_list            # 언더스코어, 복수형 미사용
```

---

## 9. 로드 밸런싱

### 알고리즘

| 알고리즘 | 설명 |
|----------|------|
| Round Robin | 순서대로 분배 |
| Weighted Round Robin | 가중치 기반 순서 분배 |
| Least Connections | 현재 연결 수가 가장 적은 서버로 |
| IP Hash | 클라이언트 IP 기반으로 고정 서버 할당 |
| Least Response Time | 응답 시간이 가장 빠른 서버로 |

### L4 vs L7 로드 밸런서

| 항목 | L4 (Transport) | L7 (Application) |
|------|-----------------|-------------------|
| 동작 계층 | TCP/UDP | HTTP/HTTPS |
| 판단 기준 | IP, Port | URL, Header, Cookie |
| 성능 | 빠름 | 상대적 느림 |
| 기능 | 단순 분배 | 콘텐츠 기반 라우팅, SSL 종료 |
| 예시 | NLB (AWS) | ALB (AWS), Nginx, HAProxy |

---

## 10. CORS (Cross-Origin Resource Sharing)

### 동일 출처 정책 (Same-Origin Policy)

- 브라우저는 보안을 위해 **다른 출처**의 리소스 접근을 차단한다
- 출처(Origin) = **프로토콜 + 도메인 + 포트** (하나라도 다르면 다른 출처)

### CORS 동작 방식

```
[Simple Request]
- GET, POST, HEAD 중 하나
- 특정 Content-Type만 (text/plain, multipart/form-data, application/x-www-form-urlencoded)
→ 바로 요청 전송, 서버 응답의 Access-Control-Allow-Origin 확인

[Preflight Request]
- 위 조건에 해당하지 않는 요청 (PUT, DELETE, application/json 등)
→ OPTIONS 메서드로 사전 요청 전송
→ 서버가 허용 여부 응답
→ 허용 시 본 요청 전송
```

### 주요 응답 헤더

```
Access-Control-Allow-Origin: https://example.com
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

---

## 11. 웹소켓 (WebSocket)

### HTTP vs WebSocket

| 항목 | HTTP | WebSocket |
|------|------|-----------|
| 통신 방식 | 요청-응답 (단방향) | 전이중 (양방향) |
| 연결 | 매 요청마다 연결/해제 | 한 번 연결 후 유지 |
| 오버헤드 | 매번 헤더 전송 | 초기 핸드셰이크 후 경량 프레임 |
| 용도 | 일반 웹 통신 | 실시간 채팅, 주식, 게임 |

### WebSocket 핸드셰이크

```
# 클라이언트 요청 (HTTP Upgrade)
GET /chat HTTP/1.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==

# 서버 응답
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

### WebSocket vs SSE vs Long Polling

| 항목 | WebSocket | SSE | Long Polling |
|------|-----------|-----|-------------|
| 방향 | 양방향 | 서버→클라이언트 | 서버→클라이언트 |
| 프로토콜 | ws:// | HTTP | HTTP |
| 재연결 | 직접 구현 | 자동 | 직접 구현 |
| 바이너리 | 지원 | 텍스트만 | 가능 |
| 적합한 경우 | 채팅, 게임 | 알림, 피드 | 호환성 중시 |

---

## 12. 네트워크 보안

### 대표 공격 유형

| 공격 | 설명 | 방어 |
|------|------|------|
| **XSS** | 악성 스크립트 삽입 | 입력 이스케이프, CSP 헤더 |
| **CSRF** | 사용자 권한으로 위조 요청 | CSRF 토큰, SameSite 쿠키 |
| **SQL Injection** | 악성 SQL 주입 | PreparedStatement, ORM |
| **DDoS** | 대량 트래픽으로 서비스 마비 | Rate Limiting, CDN, WAF |
| **MITM** | 중간자 도청/변조 | HTTPS (TLS), 인증서 피닝 |

### 대칭키 vs 비대칭키 암호화

| 항목 | 대칭키 | 비대칭키 |
|------|--------|----------|
| 키 | 암/복호화 동일 키 | 공개키 + 개인키 |
| 속도 | 빠름 | 느림 |
| 키 교환 | 어려움 | 공개키 공유로 해결 |
| 예시 | AES, DES | RSA, ECDSA |
| 용도 | 데이터 암호화 | 키 교환, 전자서명 |

---

## 13. CDN (Content Delivery Network)

- 전 세계에 분산된 **엣지 서버**에 콘텐츠를 캐시
- 사용자와 **지리적으로 가까운 서버**에서 응답 → 지연 시간 감소
- 정적 콘텐츠(이미지, CSS, JS)뿐 아니라 동적 콘텐츠 가속도 지원
- DDoS 방어 효과 (트래픽 분산)

---

## 14. 프록시 서버

### Forward Proxy vs Reverse Proxy

| 항목 | Forward Proxy | Reverse Proxy |
|------|---------------|---------------|
| 위치 | 클라이언트 앞 | 서버 앞 |
| 목적 | 클라이언트 익명화, 접근 제어 | 로드 밸런싱, 보안, 캐싱 |
| 클라이언트 인지 | 프록시를 알고 있음 | 프록시 존재를 모름 |
| 예시 | 사내 프록시, VPN | Nginx, API Gateway, CDN |

> **면접 포인트**: "Nginx가 Reverse Proxy인 이유?" → 클라이언트는 Nginx에 요청하지만, 실제로 Nginx 뒤의 WAS(Tomcat 등)가 처리한다. 클라이언트는 뒷단 서버의 존재를 모른다.

---

## 15. TCP 흐름 제어와 혼잡 제어

### 흐름 제어 (Flow Control)

- **수신자**의 처리 속도에 맞춰 송신 속도를 조절
- **슬라이딩 윈도우**: 수신자가 Window Size를 알려주면, 송신자는 그만큼만 전송
- 수신 버퍼가 가득 차면 Window Size = 0 → 전송 중단

### 혼잡 제어 (Congestion Control)

- **네트워크**의 혼잡 상황에 맞춰 송신 속도를 조절
- **Slow Start**: 초기에 윈도우를 작게 시작, 지수적 증가
- **Congestion Avoidance**: 임계치(ssthresh) 이후 선형 증가
- **Fast Retransmit**: 3개의 중복 ACK 수신 시 즉시 재전송
- **Fast Recovery**: 혼잡 윈도우를 절반으로 줄이고 선형 증가 재개

---

## 16. 자주 나오는 면접 질문 모음

1. **TCP와 UDP의 차이점을 설명하세요**
2. **3-way handshake 과정을 설명하세요**
3. **HTTP와 HTTPS의 차이점은?**
4. **브라우저에 URL을 입력하면 어떤 일이 발생하나요?**
5. **GET과 POST의 차이점은?**
6. **쿠키와 세션의 차이점은?**
7. **JWT의 장단점은?**
8. **REST API란 무엇이고, RESTful하다는 것은?**
9. **CORS가 무엇이고, 왜 필요한가요?**
10. **HTTP/2의 특징은?**
11. **로드 밸런서의 L4와 L7의 차이점은?**
12. **WebSocket과 HTTP의 차이점은?**
13. **Forward Proxy와 Reverse Proxy의 차이점은?**
14. **DNS 동작 과정을 설명하세요**
15. **대칭키와 비대칭키 암호화의 차이점은?**
16. **TCP의 흐름 제어와 혼잡 제어의 차이는?**
17. **CDN은 무엇이고, 왜 사용하나요?**
18. **XSS와 CSRF의 차이점과 방어 방법은?**
19. **HTTP 상태 코드 401과 403의 차이는?**
20. **멱등성(Idempotent)이란 무엇인가요?**
