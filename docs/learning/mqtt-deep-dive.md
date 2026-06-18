# MQTT Deep Dive

> 이 문서는 본 프로젝트(`vueroid-web-api-device`)에서 MQTT를 "튜토리얼 수준"이 아니라
> 프로토콜의 동작 원리부터 운영상의 함정까지 이해하기 위한 이론 자료입니다.
> 추상적인 설명에 그치지 않도록, 각 개념을 이 프로젝트의 실제 코드/설정과 연결합니다.
>
> 관련 코드:
> - `config/MqttConfig.java` — Publisher/Subscriber 채널 및 어댑터
> - `config/MockMqttConfig.java` — 로컬용 가짜 채널
> - `service/MqttService.java` — 수신 메시지 디스패치 + 송신 게이트웨이
> - `controller/WebHookController.java` — 브로커 → 서버 웹훅 (연결 해제 감지)
> - `resources/application.yml` — 브로커 접속 정보

---

## 0. 한눈에 보는 우리 프로젝트의 MQTT

```
                 publish  device/{serial}/report                ┌────────────────────┐
   ┌──────────┐  publish  device/{serial}/reportThumbnail       │  Spring Boot 서버    │
   │  Device   │ ───────────────────────────────────────────▶  │  (backend-group)    │
   │ (블랙박스) │                                                 │                    │
   └──────────┘  ◀─────────────────────────────────────────── │  subscribe          │
        ▲          publish  response/{serial}                   │  $share/backend-    │
        │                                                       │  group/device/+/... │
        │                  ┌─────────────┐                      └────────┬───────────┘
        └──────────────────│   NanoMQ    │◀──────────────────────────────┘
            MQTT (1883)     │   Broker    │   HTTP Webhook (client_disconnected)
                            └─────────────┘ ─────────────▶ POST /device/mqtt/webhook
```

- **브로커**: NanoMQ (`tcp://wifinlb.vueroid-cloud.com:1883`). ACL은 브로커 측 `nanomq_acl.conf`로 관리.
- **클라이언트 라이브러리**: Eclipse Paho MQTT v3 + Spring Integration MQTT.
- **QoS**: 구독 측 QoS 1 (`adapter.setQos(1)`).
- **확장(Scale-Out)**: MQTT 5 **Shared Subscription** (`$share/backend-group/...`)으로 서버 인스턴스 간 로드밸런싱.
- **세션**: `cleanSession=true` (상태를 들고 가지 않는 무상태 서버).

---

## 1. MQTT란 무엇인가 — 왜 HTTP가 아니라 MQTT인가

MQTT(Message Queuing Telemetry Transport)는 **발행/구독(Pub/Sub)** 모델 기반의 경량 메시징 프로토콜입니다.
1999년 IBM에서 송유관 원격 측정(telemetry)을 위해 설계되었고, 현재는 OASIS 표준(v3.1.1 / v5.0)입니다.

### HTTP 요청/응답 모델의 한계 (IoT 관점)
- **연결당 1요청**: 디바이스가 서버에 붙을 때마다 TCP+TLS 핸드셰이크 비용 발생.
- **서버 → 디바이스 푸시가 어렵다**: 디바이스가 NAT/방화벽 뒤에 있으면 서버가 먼저 연결할 수 없음. 폴링(polling)으로 우회하면 낭비가 큼.
- **헤더 오버헤드**: HTTP 헤더는 수백 바이트. 좁은 대역폭/배터리 환경에 부담.

### MQTT가 푸는 방식
- **상시 연결(long-lived TCP)**: 디바이스가 브로커와 한 번 연결을 맺고 유지. 이후 양방향 메시지가 이 연결 위로 흐름.
- **브로커 중개**: 디바이스와 서버는 서로를 직접 모름. 둘 다 **브로커**에만 연결하고, **토픽(topic)**으로 만난다.
- **작은 고정 헤더**: 최소 2바이트. CONNECT 이후 PUBLISH의 오버헤드가 매우 작다.

> **우리 프로젝트 맥락**: 블랙박스(디바이스)는 이동 통신망 뒤에 있어 서버가 직접 연결할 수 없습니다.
> 그래서 디바이스가 브로커에 붙어 `device/{serial}/report`로 데이터를 올리고,
> 서버는 같은 브로커를 구독해 그 데이터를 받습니다. 응답은 `response/{serial}`로 되돌려 줍니다.

---

## 2. 핵심 개념

### 2.1 Broker (브로커)
모든 메시지가 거쳐 가는 중앙 서버. 클라이언트(디바이스/서버)는 서로 직접 통신하지 않고 브로커를 통해서만 메시지를 주고받습니다.
브로커의 역할:
- 클라이언트 연결/인증/세션 관리
- 토픽 구독 정보 유지
- 발행된 메시지를 매칭되는 구독자에게 라우팅
- QoS에 따른 재전송/저장
- (구현별로) ACL, 보존 메시지, 공유 구독, 웹훅 등 부가 기능

본 프로젝트의 브로커는 **NanoMQ** — 경량/고성능 MQTT 브로커. 토픽 추가 시 `nanomq_acl.conf`에 권한을 등록해야 한다는 주석이 `MqttConfig`에 남아 있습니다.

### 2.2 Topic (토픽)
메시지의 주소. `/`로 구분되는 계층 문자열입니다.

```
device/ABC123/report
└─레벨0─┘└레벨1┘└레벨2┘
```

- 발행자는 **구체적인** 토픽으로 publish (`device/ABC123/report`).
- 구독자는 **와일드카드**로 여러 토픽을 한 번에 구독 가능.
  - `+` : **한 레벨** 와일드카드 → `device/+/report` 는 `device/ABC123/report`, `device/XYZ/report` 매칭. `device/ABC123/sub/report`는 매칭 안 됨.
  - `#` : **다중 레벨** 와일드카드 (맨 끝에만) → `device/#` 는 `device/` 하위 전부 매칭.

> **우리 프로젝트의 토픽 설계** (`MqttConfig`):
> | 방향 | 토픽 | 의미 |
> |------|------|------|
> | 디바이스→서버 | `device/+/report` | 주기 리포트 |
> | 디바이스→서버 | `device/+/reportThumbnail` | 썸네일(바이너리) |
> | 디바이스→서버 | `report/+` | 구(舊) 버전 리포트 (REPORT_V0) |
> | 서버→디바이스 | `response/{serial}` | 응답 |
>
> `MqttService.getMessageType()`는 토픽을 `/`로 잘라 레벨 2(`device/{serial}/report` → `report`)를
> 메시지 타입으로 사용합니다. **토픽 = 라우팅 키**라는 점을 코드 레벨에서 그대로 보여주는 예입니다.

#### 토픽 설계 원칙
- **일반→구체(general to specific)** 순서로 계층화: `device/{serial}/{action}`.
- 토픽에 **가변 식별자**(시리얼)를 넣어 디바이스별 분리.
- 토픽 자체가 곧 권한(ACL)의 단위가 되므로, 디바이스가 자기 토픽에만 publish하도록 ACL을 거는 것이 보안의 핵심.
- `$`로 시작하는 토픽은 **예약어**(시스템용). 일반 구독 `#`에 잡히지 않음. `$SYS/...`(브로커 통계), `$share/...`(공유 구독)가 대표적.

### 2.3 Publish / Subscribe (발행/구독)
- **느슨한 결합(decoupling)**: 발행자는 누가 받는지 모르고, 구독자는 누가 보냈는지 모름. 토픽으로만 연결.
- **시간 분리**: (보존/세션 기능과 함께라면) 발행 시점에 구독자가 접속 중이 아니어도 됨.
- **공간 분리**: 서로의 IP/위치를 알 필요 없음.

이 느슨함 덕분에 **서버를 무중단으로 늘리고 줄일 수 있습니다** (→ §6 Shared Subscription).

---

## 3. QoS — 전달 보장 수준

MQTT의 핵심 차별점. 메시지마다 "얼마나 확실히 전달할지"를 3단계로 고를 수 있습니다.

| QoS | 이름 | 전달 보장 | 핸드셰이크 | 중복 가능성 |
|-----|------|----------|-----------|-----------|
| 0 | At most once | 최대 1회 (유실 가능) | PUBLISH (단방향) | 없음 |
| 1 | At least once | 최소 1회 (중복 가능) | PUBLISH → PUBACK | **있음** |
| 2 | Exactly once | 정확히 1회 | PUBLISH→PUBREC→PUBREL→PUBCOMP | 없음 |

### QoS 0 — Fire and forget
```
Publisher ──PUBLISH──▶ Broker
```
보내고 끝. ACK 없음. 네트워크가 끊기면 유실. 가장 빠르고 가벼움. (예: 1초마다 갱신되는 센서값처럼 한 번 빠져도 무방한 데이터)

### QoS 1 — At least once  ← **우리 프로젝트가 사용**
```
Publisher ──PUBLISH(id=42)──▶ Broker
Publisher ◀──PUBACK(id=42)─── Broker
```
PUBACK을 받을 때까지 발신자가 메시지를 보관하고, 못 받으면 **재전송**합니다.
재전송 때문에 **같은 메시지가 두 번 도착할 수 있습니다(DUP 플래그가 붙음)**.

> **중요 — 멱등성(Idempotency)**: 우리는 구독 QoS를 1로 설정(`adapter.setQos(1)`)했습니다.
> 따라서 `MqttService.handleMessage()`가 **같은 리포트를 두 번 받을 수 있다**는 가정을 해야 합니다.
> `receiveService.report()`가 동일 메시지를 두 번 처리해도 결과가 같도록(멱등) 설계하거나,
> 메시지에 고유 ID/타임스탬프를 두고 중복을 걸러내야 합니다. 현재는 별도 dedup이 없으므로,
> 처리 로직이 멱등인지 점검해 볼 가치가 있습니다.

### QoS 2 — Exactly once
4-way 핸드셰이크로 중복을 원천 차단. 가장 느리고 무겁다. 결제처럼 중복이 치명적일 때만.

### QoS는 "홉(hop) 단위"다 — 흔한 오해
QoS는 **발행자↔브로커 구간**과 **브로커↔구독자 구간**에 **각각 독립적으로** 적용됩니다.
End-to-end 보장이 아닙니다.

```
Device ──QoS 1──▶ Broker ──QoS ?──▶ Server
```

- 디바이스가 QoS 1로 보내도, 서버가 구독을 QoS 0으로 했다면 브로커→서버 구간은 QoS 0.
- 실제 적용 QoS는 `min(발행 QoS, 구독 QoS)`.
- 우리는 구독을 QoS 1로 했으므로, 디바이스가 QoS 1 이상으로 발행하면 브로커→서버도 QoS 1로 전달됩니다.

---

## 4. 연결과 세션 — CONNECT, Keep-Alive, Clean Session

### 4.1 CONNECT 흐름
```
Client ──CONNECT(clientId, username, password, keepAlive, cleanSession)──▶ Broker
Client ◀──CONNACK(sessionPresent, returnCode)───────────────────────────  Broker
```
- **clientId**: 브로커가 클라이언트를 식별하는 고유 키. **전역 유일**해야 함.
- 같은 clientId로 두 클라이언트가 접속하면 **기존 연결이 강제로 끊긴다**(브로커가 먼저 온 쪽을 추방).

> **우리 프로젝트의 clientId 전략** (`MqttConfig`):
> ```java
> private static final String CLIENT_ID = "server-" + UUID.randomUUID().toString();
> // publisher: CLIENT_ID + "_pub", subscriber: CLIENT_ID + "_sub"
> ```
> 서버를 여러 대로 늘려도(Scale-Out) clientId 충돌이 없도록 **UUID로 랜덤화**합니다.
> 만약 clientId를 고정값으로 박았다면, 2번째 인스턴스가 뜨는 순간 1번째가 추방되어
> 두 서버가 서로를 끊는 "튕김(flapping)"이 발생합니다. 랜덤화는 이걸 막는 핵심 패턴입니다.

### 4.2 Keep-Alive & PINGREQ
유휴 상태에서도 연결이 살아 있음을 알리기 위해, 클라이언트는 Keep-Alive 주기마다 메시지가 없으면 **PINGREQ**를 보내고 브로커가 **PINGRESP**로 답합니다.
브로커는 `Keep-Alive × 1.5` 시간 동안 아무 패킷도 없으면 그 클라이언트를 죽은 것으로 간주하고 연결을 끊습니다(→ LWT 발동).

### 4.3 Clean Session (v3.1.1) / Clean Start + Session Expiry (v5)
- `cleanSession=true`: 연결할 때마다 **새 세션**. 끊기면 구독 정보·미전달 메시지 모두 폐기. → **무상태(stateless)**.
- `cleanSession=false`: 브로커가 clientId 기준으로 세션을 **유지**. 오프라인 동안 도착한 QoS 1/2 메시지를 쌓아 뒀다가 재접속 시 전달. → **상태유지(persistent)**.

> **우리 프로젝트는 `cleanSession=true`** (`MqttConfig`). 게다가 clientId가 매번 랜덤이므로,
> 재시작하면 이전 세션을 이어받을 수 없습니다(이어받을 대상이 사라짐).
> 이는 의도된 설계입니다 — **서버는 무상태여야 수평 확장이 자유롭기** 때문.
> 단점: 서버가 전부 내려가 있는 동안 publish된 메시지는 보관되지 않습니다.
> "메시지를 절대 놓치면 안 된다"가 요구사항이라면, 디바이스 측 retry/QoS와
> 서버 항시 가용성(여러 인스턴스 분산)으로 메워야 합니다.

### 4.4 Automatic Reconnect
`options.setAutomaticReconnect(true)` — 연결이 끊기면 Paho가 백오프(backoff)를 두고 자동 재연결.
운영 환경에서 네트워크는 반드시 끊기므로 사실상 필수 옵션입니다.

---

## 5. Last Will & Testament (LWT) 와 브로커 웹훅 — "디바이스가 죽었다"를 아는 법

### 5.1 LWT (유언 메시지)
클라이언트가 CONNECT 시 "내가 **비정상적으로** 끊기면 이 메시지를 이 토픽에 대신 발행해 줘"라고 브로커에 위임할 수 있습니다.
- 정상 종료(DISCONNECT 패킷)면 LWT는 발행되지 않음.
- 네트워크 단절/타임아웃 등 비정상 종료면 브로커가 LWT를 발행 → 다른 구독자들이 "쟤 죽었구나"를 즉시 인지.

### 5.2 우리 프로젝트의 방식 — 브로커 HTTP 웹훅
이 프로젝트는 LWT 메시지 대신, **NanoMQ의 웹훅(Webhook) 기능**으로 연결 이벤트를 받습니다.

`WebHookController` (`POST /device/mqtt/webhook`):
```java
if ("client_disconnected".equals(payload.action())) {
    webhookService.processDisconnection(payload.clientId(), payload.reason());
}
```
- 브로커는 클라이언트가 끊기면 `client_disconnected` 액션과 `clientId`, `reason`을 담아 서버 HTTP로 POST.
- 서버는 `X-Internal-Secret-Key` 헤더로 1차 검증 후 처리.

`MqttWebhookPayload` 레코드:
```java
record MqttWebhookPayload(String action, @JsonProperty("clientid") String clientId,
                          String username, String reason, @JsonProperty("ts") Long timestamp)
```

> **LWT vs 웹훅 비교**
> - **LWT**: MQTT 프로토콜 내부 기능. 다른 *MQTT 구독자*에게 알림. 디바이스↔디바이스/서버 알림에 적합.
> - **웹훅**: 브로커→백엔드 HTTP 콜백. 디바이스 연결 상태를 *백엔드 비즈니스 로직*에 태우기 좋음
>   (DB에 오프라인 기록, 알림 발송 등). 우리는 후자를 택했습니다.
>
> 주의: 웹훅 엔드포인트는 외부에 노출되므로 시크릿 키 검증이 필수입니다. 현재 키가
> 소스에 하드코딩(`VALID_KEY`)되어 있는데, 운영에서는 환경변수/시크릿 매니저로 빼는 것이 안전합니다.

---

## 6. Shared Subscription — 서버 수평 확장의 핵심

### 6.1 일반 구독의 문제
일반 토픽 구독은 **모든 구독자에게 메시지가 복제**됩니다(fan-out).
서버를 3대로 늘려 모두 `device/+/report`를 구독하면, 리포트 1건이 **3대 모두에 도착** → 3번 처리되는 사고.

### 6.2 Shared Subscription (MQTT 5 / 일부 브로커 확장)
`$share/{그룹명}/{토픽}` 형식으로 구독하면, **같은 그룹에 속한 구독자들 중 한 명에게만** 메시지가 전달됩니다(브로커가 라운드로빈/랜덤으로 분배). 그룹 = 컨슈머 그룹(Kafka의 컨슈머 그룹과 유사한 개념).

> **우리 프로젝트** (`MqttConfig.inbound()`):
> ```java
> String sharedReport = "$share/backend-group/" + "device/+/report";
> // → 실제 구독: $share/backend-group/device/+/report
> ```
> 모든 서버 인스턴스가 `backend-group`이라는 동일 그룹으로 구독합니다. 따라서:
> - 리포트 1건은 그룹 내 **단 한 대**의 서버만 받아 처리 → 중복 처리 없음.
> - 서버를 늘리면 부하가 자동 분산(로드밸런싱).
> - 한 대가 죽어도 나머지가 메시지를 이어받음(고가용성).
>
> 이것이 `cleanSession=true` + 랜덤 clientId(무상태) 설계와 맞물려, **서버를 자유롭게 늘리고 줄일 수 있는** 구조를 완성합니다.

```
                                    ┌─ Server A (backend-group) ─┐
device/X/report ──▶ Broker ──┐      │                            │
device/Y/report ──▶ Broker ──┼──────┼─ Server B (backend-group) ─┤  각 메시지는
device/Z/report ──▶ Broker ──┘      │                            │  한 대에만 분배
                                    └─ Server C (backend-group) ─┘
```

### 6.3 주의점
- **순서 보장 없음**: 같은 디바이스의 연속 메시지가 서로 다른 서버로 갈 수 있음. 메시지 간 순서 의존이 있으면 안 됨(혹은 처리 로직이 순서에 무관해야 함).
- **브로커 의존**: Shared Subscription은 MQTT 5 표준이지만, v3.1.1에선 브로커 확장(EMQX/NanoMQ/VerneMQ 등)으로 지원. NanoMQ가 이를 지원하기에 가능한 설계.
- `$share`는 예약 토픽이므로 ACL/모니터링에서 별도 취급됨.

---

## 7. Retained Message & Persistent Session — 메시지를 "남기는" 두 방법

혼동하기 쉬운 두 개념을 구분합니다.

### 7.1 Retained Message (보존 메시지)
- PUBLISH에 `retain=true`를 주면, 브로커가 **그 토픽의 마지막 메시지 1건을 보관**.
- 이후 누군가 그 토픽을 **새로 구독하면 즉시 그 보관본을 받음**.
- 용도: "최신 상태" 전달. 예) `device/X/status`에 `online`을 retain → 새 구독자는 구독 즉시 현재 상태를 알 수 있음.
- 토픽당 1건만 유지(새 retain 발행이 덮어씀). 빈 페이로드를 retain으로 보내면 보관본 삭제.

### 7.2 Persistent Session (영속 세션)
- `cleanSession=false`로 연결한 클라이언트가 **오프라인인 동안 도착한 QoS 1/2 메시지**를 브로커가 쌓아 두었다가 재접속 시 전달.
- 구독자(개별 클라이언트) 단위로 동작.

| 구분 | Retained | Persistent Session |
|------|----------|---------------------|
| 보관 대상 | 토픽의 마지막 1건 | 특정 clientId가 못 받은 메시지들 |
| 받는 시점 | 새 구독 즉시 | 재접속 시 |
| 우리 사용 여부 | 미사용 | 미사용 (cleanSession=true) |

> 본 프로젝트는 둘 다 쓰지 않습니다. "최신 상태"는 웹훅/리포트로 갱신하고, 서버는 무상태로 유지하는 설계 방향이기 때문입니다.

---

## 8. Spring Integration로 본 메시지 흐름

이 프로젝트는 Paho를 직접 다루지 않고 **Spring Integration MQTT** 위에서 메시지를 "채널(Channel)"로 다룹니다.

### 8.1 수신(Inbound) 파이프라인
```
NanoMQ ──▶ MqttPahoMessageDrivenChannelAdapter (inbound)
            └ $share/backend-group/device/+/report 등 구독, QoS 1
       ──▶ mqttInboundChannel (DirectChannel)
       ──▶ @ServiceActivator MqttService.handleMessage(payload, topic)
            └ 토픽 파싱 → messageType별 분기 → ReceiveService 호출
```
- `DefaultPahoMessageConverter`에 `setPayloadAsBytes(true)` → 페이로드를 `byte[]`로 받음.
  썸네일 같은 **바이너리 데이터**를 그대로 다루기 위함. 텍스트는 `new String(payload, UTF_8)`로 변환.
- `setCompletionTimeout(5000)` → 구독/해제 작업의 타임아웃 5초.

### 8.2 송신(Outbound) 파이프라인
```
MqttService.MqttGateway.sendToMqtt(data, topic)   ← @MessagingGateway
       ──▶ mqttOutboundChannel (DirectChannel)
       ──▶ @ServiceActivator MqttPahoMessageHandler (async)
       ──▶ NanoMQ
```
- `@MessagingGateway`는 인터페이스만 선언하면 Spring이 프록시를 만들어 채널로 메시지를 흘려보냄.
  즉 `mqttGateway.sendToMqtt("임시 응답", "response/" + serial)` 한 줄이 곧 MQTT publish.
- `setAsync(true)` → 발행을 비동기로(블로킹 없이).

### 8.3 DirectChannel의 의미
`DirectChannel`은 **호출 스레드에서 동기적으로** 핸들러를 실행합니다(중간 큐 없음).
즉 MQTT 수신 스레드가 그대로 `handleMessage`를 실행 → 무거운 처리를 여기서 동기로 하면
**메시지 수신 처리량이 떨어질 수 있음**. 처리량이 문제가 되면 `QueueChannel`/`ExecutorChannel`로
바꿔 비동기 처리하는 것을 고려할 수 있습니다(단, 그러면 순서/백프레셔를 별도로 고민해야 함).

### 8.4 Mock 설정 — 로컬 개발 분리
`MockMqttConfig`는 `spring.mqtt.enabled=false`일 때 활성화되어,
**브로커 없이도** 같은 이름의 채널 빈(`mqttInboundChannel`, `mqttOutboundChannel`)을 가짜로 제공합니다.
- Outbound로 보내도 연결된 어댑터가 없어 조용히 사라짐(에러 없음).
- 게이트웨이는 콘솔에 로그만 찍는 가짜 구현.
- `@ConditionalOnProperty`로 진짜(`MqttConfig`)와 가짜(`MockMqttConfig`)가 **상호 배타적으로** 켜짐.
  덕분에 로컬에서 브로커 의존 없이 앱을 띄울 수 있음.

---

## 9. 보안 관점

| 위협 | MQTT에서의 대응 | 본 프로젝트 현황 |
|------|----------------|------------------|
| 도청 | TLS (`ssl://`, 8883) | 현재 평문 `tcp://`(1883). 운영 시 TLS 권장 |
| 무단 접속 | username/password, 클라이언트 인증서 | username/password 사용 (`application.yml`) |
| 토픽 무단 publish/subscribe | 브로커 ACL | NanoMQ `nanomq_acl.conf`로 토픽별 권한 관리 |
| 웹훅 위조 | 시크릿 헤더/서명 | `X-Internal-Secret-Key` 검증 |
| 자격증명 노출 | 시크릿 매니저/환경변수 | 현재 yml에 평문 → 외부화 권장 |

핵심 원칙: **인증(누구냐) + 인가(무슨 토픽에 권한이 있냐)를 분리**해서 건다.
디바이스 A가 디바이스 B의 토픽(`device/B/...`)에 publish하지 못하도록 ACL을 거는 것이 IoT 보안의 기본입니다.

> 자세한 보안 점검 항목은 `docs/security-audit.md`를 함께 참고하세요.

---

## 10. 운영 체크리스트 (이 프로젝트 기준)

- [ ] **멱등성**: QoS 1 중복 수신 시 `receiveService.report()`가 안전한가? (§3.2)
- [ ] **순서 비의존**: Shared Subscription으로 메시지가 인스턴스 간 분산돼도 처리 결과가 동일한가? (§6.3)
- [ ] **TLS**: 운영 브로커 연결을 `ssl://`로 전환했는가? (§9)
- [ ] **자격증명 외부화**: yml 평문 비밀번호/웹훅 키를 환경변수·시크릿으로 뺐는가? (§9)
- [ ] **clientId 유일성**: Scale-Out 시 UUID 전략이 유지되는가? (§4.1)
- [ ] **백프레셔**: `DirectChannel` 동기 처리로 수신 스레드가 막히지 않는가? 무거운 작업은 분리했는가? (§8.3)
- [ ] **재연결**: `automaticReconnect`가 켜져 있고, 재연결 시 구독이 복구되는가? (§4.4)
- [ ] **ACL**: 새 토픽 추가 시 `nanomq_acl.conf` 갱신을 잊지 않았는가? (§2.2)

---

## 부록 A. MQTT 3.1.1 vs 5.0 주요 차이

| 기능 | v3.1.1 | v5.0 |
|------|--------|------|
| Shared Subscription | 브로커 확장(`$share`) | **표준** |
| Session Expiry | cleanSession 불리언 | 만료 시간(초) 세밀 제어 |
| 사용자 속성(User Properties) | 없음 | 메시지에 key-value 메타데이터 |
| 응답 토픽(Response Topic) | 관례적으로 직접 구현 | 프로토콜 내장(요청/응답 패턴) |
| Reason Code | 단순 | 상세한 실패 사유 코드 |
| 메시지 만료(Message Expiry) | 없음 | 지원 |

본 프로젝트는 Paho v3 클라이언트(v3.1.1)를 쓰지만, 브로커(NanoMQ)의 `$share` 확장을 빌려
Shared Subscription을 활용합니다. 요청/응답은 v5의 Response Topic 대신 `response/{serial}` 토픽 관례로 직접 구현합니다.

## 부록 B. MQTT 제어 패킷 빠른 참조

| 패킷 | 방향 | 의미 |
|------|------|------|
| CONNECT / CONNACK | C→B / B→C | 연결 요청 / 수락 |
| PUBLISH | 양방향 | 메시지 발행 |
| PUBACK | — | QoS 1 수신 확인 |
| PUBREC/PUBREL/PUBCOMP | — | QoS 2 4-way 핸드셰이크 |
| SUBSCRIBE / SUBACK | C→B / B→C | 구독 요청 / 수락 |
| UNSUBSCRIBE / UNSUBACK | C→B / B→C | 구독 해제 |
| PINGREQ / PINGRESP | C→B / B→C | Keep-Alive |
| DISCONNECT | 주로 C→B | 정상 종료 (LWT 발동 안 함) |

## 부록 C. 더 읽을거리

- MQTT 5.0 OASIS 표준 명세
- Eclipse Paho Java 클라이언트 문서
- Spring Integration MQTT 레퍼런스 (`spring-integration-mqtt`)
- NanoMQ 공식 문서 (ACL, Webhook, Shared Subscription 설정)