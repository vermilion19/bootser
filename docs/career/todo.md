# 시니어 백엔드 엔지니어 역량 강화 프로젝트 로드맵

본 문서는 대규모 트래픽 환경에서의 고가용성(High Availability) 및 신뢰성(Reliability) 확보 역량을 기르기 위한 3가지 심화 프로젝트 주제를 기술합니다.

---

## 1. 대용량 실시간 알림(Push) 게이트웨이 서버 (진행중...)

**주제: 동시 접속자 10만 명을 버티는 SSE/WebSocket 서버 구현**

이 프로젝트는 **TCP 커넥션 유지 비용**에 대한 이해와 대규모 객체 생존 시 발생할 수 있는 **OOM(Out Of Memory) 방지 및 메모리 관리**를 학습하는 데 중점을 둡니다.

### 핵심 시나리오
* **대규모 연결 유지:** 10만 개의 더미 클라이언트가 서버에 접속하여 세션(SSE 또는 WebSocket)을 지속적으로 유지.
* **실시간 브로드캐스팅:** 서버는 1초마다 연결된 모든 클라이언트에게 특정 데이터(예: 현재 주가 정보)를 동시에 전송.

### 주요 학습 포인트
1.  **비동기 및 논블로킹 I/O (Spring WebFlux + Netty)**
    * Thread per Request 모델의 한계(Context Switching 비용, 메모리 부족) 체험.
    * 소수의 쓰레드로 다수의 커넥션을 처리하는 Event Loop 모델의 이해 및 적용.
2.  **OS 및 네트워크 레벨 튜닝**
    * 리눅스 File Descriptor(Open Files) 제한 해제 방법.
    * TCP 소켓 송수신 버퍼 크기 조절 및 TCP Keep-Alive 파라미터 튜닝.
3.  **JVM 및 GC 최적화**
    * Long-lived Connection 객체들이 Heap 메모리에 쌓일 때의 GC 동작 분석.
    * Old Generation 영역 관리 및 G1GC/ZGC 튜닝을 통한 Stop-the-world 시간 최소화 경험.

---

## 2. Java로 직접 만드는 경량 리버스 프록시 (Mini-Zuul)

**주제: Netty를 사용하여 Nginx 수준의 경량 로드밸런서 직접 구현**

스프링 부트의 추상화된 계층을 벗어나, 네트워크 프레임워크인 Netty를 직접 제어해보며 **로우 레벨 네트워크 프로그래밍** 역량을 기르는 프로젝트입니다.

### 핵심 시나리오
* **트래픽 중계:** 클라이언트의 요청(Inbound)을 받아 내부의 백엔드 서버(Outbound)로 전달하는 중계 서버 개발.
* **기능 확장:** 요청/응답 헤더 조작, 비동기 요청 로그 파일 기록, 로드밸런싱 알고리즘(Round Robin 등) 구현.

### 주요 학습 포인트
1.  **메모리 모델 심화 (Direct Buffer vs Heap Buffer)**
    * 네트워크 패킷 처리 시 JVM Heap을 거치지 않는 Direct Buffer(Off-heap)의 성능적 이점 이해.
    * ByteBuf의 Reference Counting 및 메모리 누수(Memory Leak) 디버깅 경험.
2.  **Netty 쓰레드 모델**
    * 연결을 수락하는 Boss Group과 I/O를 처리하는 Worker Group의 역할 분리 및 설정.
3.  **장애 분석 및 트러블슈팅**
    * 백엔드 서버 지연 발생 시 프록시 서버에 미치는 영향 분석.
    * jstack을 활용한 쓰레드 덤프 분석으로 블로킹(Blocking) 지점 식별 및 해결.

---

## 3. 고성능 로그 수집 & 분석 파이프라인

**주제: 초당 5만 건의 로그를 유실 없이 파일 및 DB에 저장하는 시스템 구축**

극한의 쓰기(Write) 트래픽 상황에서 **데이터 정합성**을 보장하고 **I/O 처리 성능**을 극대화하는 방법을 학습합니다.

### 핵심 시나리오
* **고속 인입:** HTTP 요청을 통해 초당 5만 건 이상의 로그 데이터 수신.
* **버퍼링 및 배치 처리:** 즉시 DB에 저장하지 않고 내부 큐(RingBuffer 등)에 적재 후 배치(Batch) 단위로 비동기 파일 쓰기 및 Kafka 전송 수행.

### 주요 학습 포인트
1.  **생산자-소비자 패턴 (Producer-Consumer Pattern)**
    * BlockingQueue의 성능 한계 확인 및 LMAX Disruptor 등 고성능 큐 라이브러리 활용.
    * Lock-free 알고리즘에 대한 이해.
2.  **Backpressure (배압) 제어**
    * 데이터 인입 속도가 처리 속도를 초과할 때의 제어 전략 수립(데이터 드롭, 요청 거부, 버퍼 확장 등).
    * Reactive Streams(WebFlux)의 배압 처리 메커니즘 이해.
3.  **커널 I/O 파라미터 이해**
    * 디스크 I/O 성능 향상을 위한 리눅스 Page Cache 동작 원리 이해.
    * 데이터 내구성(Durability) 보장을 위한 fsync 옵션과 성능 간의 트레이드오프 분석.