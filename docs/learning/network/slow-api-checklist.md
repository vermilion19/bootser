- API 응답이 갑자기 느려짐
```
DB 느림
GC pause
CPU 부족
TCP congestion
network packet loss
```

- tcp 분석
```
ss -ti

출력
cwnd:10
rtt:120ms
retrans:20

의미
네트워크 packet loss 발생
→ TCP congestion control 작동
→ throughput 감소

이런 튜닝 가능
tcp_window_scaling
tcp_rmem (read memory)
tcp_wmem (write memory)
```

- 100k TCP connections
```
socket buffer memory 증가
epoll wakeup 증가
congestion window 감소

NIC interrupt
↓
kernel network stack
↓
socket ready
↓
epoll wakeup


epoll_wait wakeup 증가
context switch 증가
CPU usage 증가
특히 many idle connections 환경에서 많이 발생

등장한 기술
SO_REUSEPORT
IO_uring
NAPI


TCP는 각 connection마다 여러 타이머를 가진다.
ex)
retransmission timer
delayed ACK timer
keepalive timer
TIME_WAIT timer
Linux 커널은 이를 timer wheel로 관리

패킷이 들어오면 커널은 이 connection을 찾아야 한다. 즉 TCP 4-tuple lookup
src IP
src port
dst IP
dst port
```