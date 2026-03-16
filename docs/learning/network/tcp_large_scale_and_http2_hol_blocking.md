# TCP 대규모 트래픽 운영 이슈 정리

## 1. TCP Connection이 10만 개 이상일 때 발생하는 커널 병목

대규모 서비스(WebSocket, HTTP Keep‑Alive, gRPC 등)는 수십만 TCP
connection을 유지하는 경우가 많다.\
이때 Linux 커널에서 다음과 같은 병목이 발생할 수 있다.

### 1.1 Socket Buffer 메모리 증가

각 TCP connection은 최소 두 개의 버퍼를 가진다.

-   **Send Buffer (wmem)**
-   **Receive Buffer (rmem)**

예:

    send buffer ≈ 128KB
    receive buffer ≈ 128KB

1 connection ≈ 256KB

    256KB × 100,000 connections ≈ 25GB

따라서 TCP buffer만으로도 수십 GB 메모리가 사용될 수 있다.

관련 커널 파라미터

    net.ipv4.tcp_rmem
    net.ipv4.tcp_wmem
    net.ipv4.tcp_mem

------------------------------------------------------------------------

### 1.2 epoll wakeup storm

대규모 connection 환경에서는 다음 흐름이 반복된다.

    NIC interrupt
     ↓
    kernel network stack
     ↓
    socket ready
     ↓
    epoll wakeup

connection 수가 많으면

-   epoll wakeup 증가
-   context switch 증가
-   CPU 사용량 증가

관련 기술

-   SO_REUSEPORT
-   NAPI
-   io_uring

------------------------------------------------------------------------

### 1.3 TCP Timer 관리 비용

TCP는 각 connection마다 여러 타이머를 가진다.

대표적인 타이머

-   retransmission timer
-   delayed ACK timer
-   keepalive timer
-   TIME_WAIT timer

connection 수가 많아지면 타이머 관리 비용도 증가한다.

Linux는 **timer wheel** 구조로 관리하지만 여전히 CPU 비용이 발생한다.

------------------------------------------------------------------------

### 1.4 Connection Lookup 비용

패킷이 들어오면 커널은 connection을 찾아야 한다.

검색 키

    src IP
    src port
    dst IP
    dst port

즉 **TCP 4‑tuple lookup**이다.

커널 내부에서는 hash table을 사용한다.

connection 수가 증가하면

-   hash collision 증가
-   lookup 비용 증가

관련 파라미터

    net.ipv4.tcp_max_syn_backlog
    net.core.somaxconn

------------------------------------------------------------------------

### 1.5 TIME_WAIT 폭증

connection이 빠르게 생성/종료되면

    TIME_WAIT

상태가 증가한다.

TIME_WAIT 유지 시간

    ≈ 60초

예:

    10k requests/sec

이면

    ≈ 600k TIME_WAIT sockets

발생 가능하다.

관련 튜닝

    net.ipv4.tcp_tw_reuse
    net.ipv4.tcp_fin_timeout

------------------------------------------------------------------------

## 2. HTTP/2에서 Head-of-Line Blocking이 완전히 해결되지 않는 이유

HTTP/2는 **multiplexing**을 도입했다.

구조

    HTTP/2 streams
         ↓
    single TCP connection

예:

    stream1
    stream2
    stream3
    stream4

HTTP/1.1의 문제였던 **request serialization**을 해결했다.

하지만 TCP의 특성 때문에 완전히 해결되지 않았다.

------------------------------------------------------------------------

### 2.1 TCP In-order Delivery

TCP는 **패킷 순서를 보장**한다.

예:

    packet1
    packet2
    packet3
    packet4

packet2가 손실되면

    packet1 → deliver
    packet3 → hold
    packet4 → hold

즉 packet2가 재전송될 때까지 기다린다.

이것을

    TCP Head-of-Line Blocking

이라고 한다.

------------------------------------------------------------------------

### 2.2 HTTP/2에서 발생하는 문제

HTTP/2 multiplexing 상황

    stream1 → packet1
    stream2 → packet2
    stream3 → packet3
    stream4 → packet4

packet2가 loss되면

    packet1 → deliver
    packet3 → hold
    packet4 → hold

결과

    stream3
    stream4

도 함께 지연된다.

이것이 **HTTP/2 HOL Blocking**이다.

------------------------------------------------------------------------

## 3. HTTP/3 (QUIC)이 등장한 이유

HTTP/3 구조

    HTTP/3
     ↓
    QUIC
     ↓
    UDP

QUIC 특징

    multiple streams
    independent packet ordering

즉 packet loss가 발생해도

    해당 stream만 영향

받는다.

그래서

    HTTP/3 = TCP HOL blocking 해결

을 목표로 설계되었다.

------------------------------------------------------------------------

# 핵심 요약

## 대규모 TCP connection 병목

-   socket buffer memory
-   epoll wakeup storm
-   TCP timer 관리 비용
-   connection lookup 비용
-   TIME_WAIT 폭증

## HTTP/2 HOL Blocking

HTTP/2는 multiplexing을 제공하지만

    TCP in-order delivery

때문에

    packet loss → 모든 stream blocking

문제가 발생한다.

이를 해결하기 위해 **HTTP/3 (QUIC)** 이 등장했다.
