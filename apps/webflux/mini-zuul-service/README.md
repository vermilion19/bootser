# Mini-Zuul: High-Performance Asynchronous Reverse Proxy

Netty와 Spring Boot를 기반으로 구현한 고성능 비동기 리버스 프록시(Reverse Proxy) 서버입니다.
기존 서블릿 기반(Tomcat)의 Blocking I/O 한계를 극복하고, 직접적인 TCP/HTTP 패킷 제어를 통해 **로드 밸런싱, 장애 조치(Failover), 트래픽 관측** 기능을 밑바닥부터 구현했습니다.

## 🚀 Key Features

* **Non-blocking Event-Driven Architecture**: Netty의 EventLoop 모델을 활용하여 소수의 스레드로 대규모 동시 접속(C10K) 처리를 지향합니다.
* **Custom Load Balancing**: Round-Robin 알고리즘을 적용하여 트래픽을 여러 백엔드 서버로 분산합니다.
* **High Availability (Failover)**: Passive Health Check 방식을 적용, 백엔드 서버 연결 실패 시 즉시 다음 서버로 재시도(Retry)하여 무중단 서비스를 제공합니다.
* **Traffic Observability**: Netty `AttributeMap`을 활용하여 요청 처리 시간(Latency) 및 접속 로그(Access Log)를 정밀하게 측정합니다.
* **Header Manipulation**: `X-Forwarded-For` 등 프록시 필수 헤더를 자동 주입하고 요청/응답을 제어합니다.
* **Memory Leak Detection**: Direct Buffer의 `ReferenceCount`를 엄격하게 관리하여 메모리 누수(Memory Leak)를 방지했습니다.

## 🛠 Tech Stack

* **Language**: Java 25
* **Framework**: Spring Boot 4.0
* **Core Network Library**: Netty 4.2
* **Build Tool**: Gradle

## 🏗 Architecture

```text
[Client] <---> [Mini-Zuul Proxy] <---> [Backend Server Group]
(Browser)      (Port: 8888)             (Port: 8081, 8082, ...)

1. Inbound Channel  : 클라이언트 요청 수신 (NioSocketChannel)
2. Proxy Handler    : 헤더 조작, 로드 밸런싱 타겟 결정, 큐(Queue) 관리
3. Outbound Channel : 백엔드 서버로 비동기 전송 (Keep-Alive 지원)

# 터미널 A (Server 1)
python backend.py 8081

# 터미널 B (Server 2)
python backend.py 8082
```

## 📝 Technical Deep Dive (Implementation Details)

### 1. Why Netty over Tomcat?
Tomcat과 같은 전통적인 서블릿 컨테이너의 'Thread-per-Request' 모델은 대량의 커넥션 유지 시 컨텍스트 스위칭(Context Switching) 비용이 높습니다. 본 프로젝트는 **Netty의 비동기 소켓 채널(NIO)**을 직접 제어하여, I/O 작업이 완료될 때만 스레드를 사용하는 방식으로 리소스 효율과 처리량을 극대화했습니다.

### 2. Backpressure & Memory Management
* **Pending Queue 전략**: 백엔드 연결이 맺어지기 전 들어온 요청을 `Queue`에 임시 저장하고, 연결 완료(Active) 이벤트 발생 시 즉시 플러시(Flush)하는 비동기 처리를 구현했습니다.
* **Reference Counting**: Netty의 `ByteBuf`는 JVM Heap이 아닌 Direct Memory를 사용하므로 GC 대상이 아닙니다. `retain()`과 `release()`를 명시적으로 호출하여 메모리 누수(Memory Leak)를 방지하고 `PARANOID` 레벨의 누수 탐지 테스트를 통과했습니다.

### 3. Failover Strategy (Passive Health Check)
별도의 헬스 체크 스레드(Active Check)를 두지 않고, 실제 사용자 요청 처리 중 연결 거부(Connection Refused)가 발생하면 이를 즉시 감지합니다. `ChannelFutureListener`에서 실패를 포착하여 재귀적으로 다음 순서의 서버로 연결을 시도하는 **Passive Failover** 로직을 통해 오버헤드 없이 고가용성을 확보했습니다.

## 📊 Access Log Example

프록시를 통과하는 모든 트래픽의 지연 시간을 나노초(ns) 단위로 측정하여 기록합니다. 이를 통해 병목 구간이 프록시인지 백엔드인지 즉시 파악할 수 있습니다.

```text
ACCESS_LOG: [/0:0:0:0:0:0:0:1:54321] -> [localhost/127.0.0.1:8081] : /api/v1/test (Took 12.45 ms)
ACCESS_LOG: [/0:0:0:0:0:0:0:1:54322] -> [localhost/127.0.0.1:8082] : /favicon.ico (Took 3.10 ms)