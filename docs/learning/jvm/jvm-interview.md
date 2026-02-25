# JVM & GC 면접 핵심 정리

## 1. JVM 구조 전체 그림

```
┌─────────────────────────────────────────────────────┐
│                   Java Source (.java)                │
└───────────────────────┬─────────────────────────────┘
                        │ javac
┌───────────────────────▼─────────────────────────────┐
│                  Bytecode (.class)                   │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│                       JVM                            │
│  ┌─────────────────────────────────────────────┐    │
│  │           Class Loader Subsystem            │    │
│  │   Bootstrap → Extension → Application      │    │
│  └──────────────────┬──────────────────────────┘    │
│                     │                                │
│  ┌──────────────────▼──────────────────────────┐    │
│  │          Runtime Data Areas                 │    │
│  │  ┌──────────┐ ┌───────┐ ┌────────────────┐ │    │
│  │  │   Heap   │ │ Stack │ │  Metaspace     │ │    │
│  │  │(공유)    │ │(스레드│ │  (Method Area) │ │    │
│  │  │          │ │ 별도) │ │                │ │    │
│  │  └──────────┘ └───────┘ └────────────────┘ │    │
│  │  ┌──────────┐ ┌─────────────────────────┐  │    │
│  │  │PC Register│ │ Native Method Stack     │  │    │
│  │  └──────────┘ └─────────────────────────┘  │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │             Execution Engine                 │   │
│  │  Interpreter → JIT Compiler → GC            │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 2. Runtime Data Areas (메모리 구조)

### Heap (힙)

모든 스레드가 공유하는 영역. **객체와 배열**이 저장된다. GC의 주 대상.

```
┌────────────────────────────────────────────────────┐
│                       Heap                         │
│                                                    │
│  ┌─────────────────────────┐  ┌──────────────────┐ │
│  │    Young Generation     │  │ Old Generation   │ │
│  │  ┌───────┬────┬────┐    │  │                  │ │
│  │  │ Eden  │ S0 │ S1 │    │  │  오래 살아남은   │ │
│  │  │       │    │    │    │  │  객체들          │ │
│  │  └───────┴────┴────┘    │  │                  │ │
│  │   Minor GC 발생         │  │  Major GC 발생   │ │
│  └─────────────────────────┘  └──────────────────┘ │
└────────────────────────────────────────────────────┘
```

| 영역 | 설명 |
|------|------|
| **Eden** | 새로 생성된 객체가 최초로 할당되는 공간 |
| **Survivor 0/1 (S0/S1)** | Minor GC에서 살아남은 객체가 이동. 항상 둘 중 하나는 비어 있음 |
| **Old Generation** | Survivor에서 age 임계값(기본 15)을 넘긴 객체가 Promotion |

### Stack (스택)

스레드마다 별도로 생성. **Stack Frame** 단위로 메서드 호출 관리.

```
┌─────────────────────┐
│   Thread 1 Stack    │
│  ┌───────────────┐  │
│  │  Frame: main  │  │  - 지역 변수
│  ├───────────────┤  │  - 피연산자 스택
│  │ Frame: method │  │  - 현재 클래스의 상수풀 참조
│  └───────────────┘  │
└─────────────────────┘
```

> **StackOverflowError**: 재귀 호출이 무한히 깊어져 Stack 영역이 가득 찰 때 발생.

### Metaspace (메타스페이스)

Java 8부터 **PermGen을 대체**. 클래스 메타데이터(클래스 구조, 메서드 정보, static 변수)를 저장.

| 항목 | PermGen (Java 7 이하) | Metaspace (Java 8+) |
|------|----------------------|---------------------|
| 위치 | JVM 힙 내부 | Native Memory (OS) |
| 크기 | 고정 (-XX:MaxPermSize) | 동적 확장 (기본 무제한) |
| OOM | OutOfMemoryError: PermGen space | OutOfMemoryError: Metaspace |

> **면접 포인트**: "왜 PermGen을 없앴나?" → 크기가 고정되어 클래스가 많아지면 OOM 발생. Metaspace는 OS 메모리를 동적으로 사용해 이 문제를 해결.

### PC Register & Native Method Stack

| 영역 | 설명 |
|------|------|
| **PC Register** | 스레드별로 현재 실행 중인 JVM 명령어 주소를 가리킴 |
| **Native Method Stack** | C/C++ 등 네이티브 코드 실행 시 사용 |

---

## 3. GC (Garbage Collection)

### GC의 기본 원리

**GC Roots에서 시작해 참조 그래프를 탐색**, 도달할 수 없는 객체를 쓰레기로 판단.

GC Roots 대상:
- 스택 프레임의 지역 변수
- static 변수
- JNI 참조

```
GC Root ─── 객체 A ─── 객체 B (도달 가능 → 살아남음)
GC Root ─── 객체 C

                        객체 D (도달 불가 → 수거 대상)
```

### GC 알고리즘 3가지 기법

| 기법 | 설명 | 단점 |
|------|------|------|
| **Mark & Sweep** | 살아있는 객체 마킹 후 미마킹 객체 제거 | 메모리 단편화 발생 |
| **Mark & Compact** | Sweep 후 살아있는 객체를 한쪽으로 압축 | 압축 비용 큼 |
| **Copying** | 살아있는 객체를 다른 공간으로 복사, 원본 전체 초기화 | 메모리 절반만 사용 가능 |

> Young Generation은 **Copying** 방식 (Eden → Survivor), Old Generation은 **Mark & Compact** 방식 사용.

### Minor GC 흐름

```
1. Eden이 가득 참 → Minor GC 트리거
2. Eden + S0의 살아있는 객체 → S1으로 복사 (age++)
3. age >= 임계값(15)인 객체 → Old Generation으로 Promotion
4. Eden과 S0 전체 초기화
5. S0 ↔ S1 역할 교체
```

### GC 종류별 비교

| GC | 특징 | STW | 대상 |
|----|------|-----|------|
| **Serial GC** | 단일 스레드 | 길다 | 단일 CPU, 소규모 |
| **Parallel GC** | 멀티 스레드 GC (처리량 우선) | 길지만 빠름 | Java 8 기본값 |
| **CMS GC** | Concurrent Mark & Sweep, STW 최소화 | 짧다 | Deprecated (Java 14) |
| **G1 GC** | Region 기반, 예측 가능한 STW | 짧다 | Java 9+ 기본값 |
| **ZGC** | 대부분의 작업을 Concurrent, STW < 10ms | 매우 짧다 | Java 15+ (대용량 힙) |

### Stop-The-World (STW)

GC 실행 중 **GC 스레드를 제외한 모든 스레드를 일시 정지**하는 현상.

```
정상 실행:  T1 ──────────────────────────────────
            T2 ──────────────────────────────────
            T3 ──────────────────────────────────

STW 발생:   T1 ─────┤STW 구간 (GC 수행)├──────────
            T2 ─────┤    모두 정지       ├──────────
            T3 ─────┤                   ├──────────
                         GC Thread ✓
```

> GC 튜닝의 목표는 **STW 시간을 줄이고 예측 가능하게** 만드는 것.

### 참조 유형 (Reference Types)

| 참조 종류 | GC 수거 시점 | 용도 |
|-----------|-------------|------|
| **Strong Reference** | 참조가 존재하는 한 수거 안 됨 | 일반적인 `new` 객체 |
| **Soft Reference** | 메모리 부족 시 수거 | 캐시 구현 |
| **Weak Reference** | GC 실행 시마다 수거 | `WeakHashMap`, 이벤트 리스너 |
| **Phantom Reference** | finalize 후 수거 | 리소스 정리 후처리 |

---

## 4. JIT 컴파일러

JVM은 처음에 **Interpreter**로 바이트코드를 한 줄씩 실행. 자주 호출되는 코드(**Hotspot**)는 **JIT Compiler**가 네이티브 코드로 컴파일하여 캐시.

```
바이트코드
    │
    ├─ 처음 실행: Interpreter (느림)
    │
    ├─ 반복 호출 감지 (Profiling)
    │
    └─ JIT 컴파일 → 네이티브 코드 (빠름)
```

> **면접 포인트**: "JVM이 처음에 느린 이유?" → JIT 컴파일 전에는 Interpreter가 실행하기 때문. Warm-up이 필요한 이유.

---

## 5. 클래스 로딩

### ClassLoader 계층 구조

```
Bootstrap ClassLoader (C++, JVM 내장)
        │  rt.jar, java.lang 등 JDK 핵심 클래스
        ▼
Extension ClassLoader
        │  jre/lib/ext 확장 라이브러리
        ▼
Application ClassLoader
           클래스패스의 클래스 (개발자가 작성한 코드)
```

### 동작 방식: 위임 모델 (Delegation Model)

클래스 로딩 요청이 오면 **자식 → 부모 순으로 위임**, 부모가 로드 못하면 자식이 로드.

```
App ClassLoader 요청
    → Extension에 위임
        → Bootstrap에 위임
            → Bootstrap이 못 찾음
        ← Extension이 시도
            → Extension이 못 찾음
    ← App ClassLoader가 최종 로드
```

> **왜 위임 모델?** 악의적인 코드가 `java.lang.String`을 재정의하지 못하도록 보안 보장.

---

## 6. OOM (OutOfMemoryError) 유형

| 에러 메시지 | 원인 | 해결 |
|-------------|------|------|
| `Java heap space` | 힙 메모리 부족, 객체 누수 | `-Xmx` 증가, 메모리 누수 수정 |
| `GC overhead limit exceeded` | GC가 98% 시간 사용, 2%만 회수 | 힙 증가, 알고리즘 개선 |
| `Metaspace` | 클래스 로딩 과다 (동적 프록시, 플러그인) | `-XX:MaxMetaspaceSize` 설정 |
| `unable to create native thread` | OS의 스레드 수 한계 초과 | 스레드 풀 크기 조정 |

### 메모리 누수 주요 원인

```java
// 1. static 컬렉션에 계속 추가
private static List<Object> list = new ArrayList<>();

// 2. 닫지 않은 리소스
InputStream is = new FileInputStream("file");
// is.close() 누락

// 3. 잘못된 equals/hashCode로 HashMap 무한 증가
Map<Object, String> map = new HashMap<>();
// equals 미구현 → 같은 키가 계속 새로운 키로 인식
```

---

## 7. JVM 튜닝 핵심 옵션

| 옵션 | 설명 | 예시 |
|------|------|------|
| `-Xms` | 힙 초기 크기 | `-Xms512m` |
| `-Xmx` | 힙 최대 크기 | `-Xmx2g` |
| `-XX:+UseG1GC` | G1GC 사용 | |
| `-XX:MaxGCPauseMillis` | GC 목표 최대 정지 시간 | `-XX:MaxGCPauseMillis=200` |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 시 힙 덤프 생성 | |
| `-verbose:gc` | GC 로그 출력 | |

> **실무 팁**: `-Xms`와 `-Xmx`를 같게 설정하면 힙 크기 조정 overhead를 없애 성능 안정화.

---

## 8. 면접 Q&A

**Q. Java는 컴파일 언어인가, 인터프리터 언어인가?**
> 둘 다다. `.java` → `.class`(바이트코드) 컴파일 후, JVM이 인터프리터로 실행한다. 단, JIT 컴파일러가 Hotspot 코드를 네이티브로 컴파일하므로 실질적으로는 혼합 방식이다.

**Q. GC가 일어나는 시점을 개발자가 제어할 수 있나?**
> `System.gc()`를 호출할 수 있지만 보장되지 않는다. GC는 JVM이 알아서 결정한다. `System.gc()`는 힌트일 뿐이며 실무에서는 호출하지 않는다.

**Q. Young Generation이 왜 Eden과 Survivor 두 공간으로 나뉘나?**
> Copying GC를 사용하기 위해서다. 살아있는 객체를 다른 공간으로 복사한 뒤 원본을 통째로 초기화하면 메모리 단편화 없이 빠르게 처리할 수 있다. Survivor를 두 개로 나누는 이유는 항상 한 곳을 빈 상태로 유지해 복사 대상 공간으로 쓰기 위함이다.

**Q. Minor GC는 STW가 없나?**
> 아니다. Minor GC도 STW가 발생한다. 다만 Young Generation이 상대적으로 작아 STW 시간이 짧을 뿐이다.

**Q. G1GC가 CMS를 대체한 이유는?**
> CMS는 단편화 문제가 있고 Full GC 발생 시 STW가 매우 길다. G1GC는 힙을 고정 크기 Region으로 나눠 단편화를 줄이고, 목표 STW 시간(-XX:MaxGCPauseMillis)을 지키도록 설계되어 더 예측 가능한 성능을 제공한다.

**Q. ZGC의 STW가 짧은 이유는?**
> 대부분의 GC 작업(Mark, Relocate)을 애플리케이션 스레드와 동시에(Concurrent) 수행하기 때문이다. Load Barrier를 통해 객체 이동 중에도 올바른 참조를 보장한다. 힙 크기에 관계없이 STW가 수 ms 이하를 유지한다.