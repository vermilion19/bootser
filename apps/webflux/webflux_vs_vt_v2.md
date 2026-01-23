# WebFlux vs Virtual Thread: OS별 성능 차이 분석

> 운영체제(OS)마다 I/O 처리 방식이 다르기 때문에 발생하는 기술적인 주제입니다.

---

## 1. I/O 모델의 차이: IOCP vs Kqueue vs Epoll

운영체제가 네트워크 요청을 처리하는 방식(커널 레벨)이 근본적으로 다릅니다.

### Windows: IOCP (Input/Output Completion Port)

| 항목 | 설명 |
|------|------|
| **방식** | Completion 기반 (Proactor 패턴) |
| **작동 방식** | *"운영체제야, 이 작업 좀 해줘. 끝나면(Completion) 나한테 알려줘."* |
| **특징** | OS가 I/O 작업을 완료한 후 애플리케이션에 통지 |

### Mac: Kqueue / Linux: Epoll

| 항목 | 설명 |
|------|------|
| **방식** | Readiness 기반 (Reactor 패턴) |
| **작동 방식** | *"운영체제야, 이 소켓들이 준비되면(Readiness) 나한테 알려줘."* |
| **특징** | 준비된 소켓 목록을 받아서 애플리케이션이 직접 I/O 수행 |

### 핵심 차이

```
IOCP (Windows)
  → OS가 I/O 완료까지 처리 → 결과만 전달

Kqueue/Epoll (Mac/Linux)
  → OS가 "준비됨"만 알려줌 → 애플리케이션이 I/O 수행
```

**Netty(WebFlux의 엔진)**는 Reactor 패턴 기반으로 설계되어 Kqueue/Epoll 방식과 자연스럽게 맞습니다.

---

## 2. Netty의 Native Transport

### Windows에서의 Netty

```
Netty → JDK NIO → Windows Kernel
        ↑
    중간 계층 존재
```

- Netty는 윈도우에서 **JDK의 표준 NIO**를 사용
- Windows용 Native Transport가 없어서 추가적인 추상화 계층을 거침

### Mac/Linux에서의 Netty

```
Netty → Native Transport (Kqueue/Epoll) → Kernel
                    ↑
            JNI를 통한 직접 통신
```

- JDK를 거치지 않고 **커널과 직접 통신** 가능
- 중간 계층이 줄어들어 I/O 처리 효율 상승

### ⚠️ 주의: Native Transport는 자동 활성화가 아님

```xml
<!-- Mac용 의존성 추가 필요 -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-kqueue</artifactId>
    <classifier>osx-aarch_64</classifier> <!-- M1/M2 -->
</dependency>

<!-- Linux용 의존성 추가 필요 -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-epoll</artifactId>
    <classifier>linux-x86_64</classifier>
</dependency>
```

**Spring WebFlux 기본 설정은 NIO를 사용**합니다. Native Transport의 성능 이점을 얻으려면 별도 설정이 필요합니다.

---

## 3. Java Virtual Thread의 현재 상태

### ⚠️ 현재 한계 (Java 21 기준)

| 항목 | 상태 |
|------|------|
| **IOCP 직접 활용** | ❌ 미지원 (향후 개선 예정) |
| **Epoll/Kqueue 직접 활용** | ❌ 미지원 |
| **현재 구현** | 내부적으로 poll 기반 동작 |

Virtual Thread가 IOCP를 직접 활용해서 Windows에서 더 빠르다는 것은 **현재 기준으로 정확하지 않습니다**. 향후 JDK 버전에서 개선될 수 있는 영역입니다.

---

## 4. 실제 성능 차이 요인

테스트 환경에서 OS별 성능 차이가 발생했다면, 다음 요인들을 고려해야 합니다:

| 요인 | 설명 |
|------|------|
| **Native Transport 설정 여부** | Mac/Linux에서 Netty Native Transport가 활성화되었는지 |
| **JVM 빌드 차이** | OS별 OpenJDK 빌드 최적화 차이 |
| **테스트 환경** | 백그라운드 프로세스, 메모리 상태, 네트워크 스택 |
| **GC 동작** | OS별 메모리 관리 방식에 따른 GC 패턴 차이 |
| **localhost 루프백** | OS별 네트워크 루프백 성능 차이 |

---

## 5. 운영 환경 관점

### 대부분의 서버는 Linux

```
┌─────────────────────────────────────────────────────────┐
│  실제 운영 서버: 대부분 Linux (AWS, GCP, Azure 등)      │
│  → Epoll + Netty Native Transport 조합이 최적화됨       │
└─────────────────────────────────────────────────────────┘
```

### 기술 선택 시 고려사항

| 상황 | 권장 |
|------|------|
| **단순한 CRUD API** | Virtual Thread (코드 단순성) |
| **고성능 I/O 집약적 서버** | WebFlux + Native Transport |
| **기존 블로킹 코드 마이그레이션** | Virtual Thread |
| **스트리밍/실시간 처리** | WebFlux |

---

## 6. 핵심 비교표

| 구분 | Windows | Mac/Linux |
|------|---------|-----------|
| **I/O 모델** | IOCP (Completion 기반) | Kqueue/Epoll (Readiness 기반) |
| **Netty Native Transport** | ❌ 미지원 | ✅ 지원 (별도 설정 필요) |
| **WebFlux 최적화** | 제한적 | 높음 |
| **운영 환경 대표성** | 낮음 | **높음** |

---

## 7. 면접 답변 예시

```
"OS별 성능 차이는 I/O 모델의 차이에서 비롯됩니다.

Windows는 IOCP(Completion 기반), Linux/Mac은 Epoll/Kqueue(Readiness 기반)를
사용하는데, Netty는 Reactor 패턴 기반이라 Readiness 모델과 더 자연스럽게 맞습니다.

또한 Netty는 Linux/Mac에서 Native Transport를 통해 JDK NIO를 거치지 않고
커널과 직접 통신할 수 있어 성능 이점이 있습니다.
다만 이는 별도 의존성 추가와 설정이 필요합니다.

대부분의 운영 서버가 Linux인 점을 고려하면,
Linux 환경에서의 테스트 결과가 실제 운영 성능을 더 잘 대변합니다."
```

---

## 참고 자료

- [Netty Native Transport 공식 문서](https://netty.io/wiki/native-transports.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Project Loom GitHub](https://github.com/openjdk/loom)
