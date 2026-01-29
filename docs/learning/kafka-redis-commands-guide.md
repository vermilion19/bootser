# Kafka & Redis 명령어 가이드 (CLI + Java 코드 매핑)

---

# Part 1. Kafka 명령어

## 1. 토픽(Topic) 관리

### 토픽 생성

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --create \
  --topic waiting-called-events \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=604800000      # 7일
```

```java
// AdminClient 사용
@RequiredArgsConstructor
@Service
public class KafkaAdminService {

    private final AdminClient adminClient;

    public void createTopic(String topicName, int partitions, short replication) {
        NewTopic topic = new NewTopic(topicName, partitions, replication);
        topic.configs(Map.of("retention.ms", "604800000"));
        adminClient.createTopics(List.of(topic));
    }
}

// Spring Boot 자동 생성 (선언형)
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic waitingCalledTopic() {
        return TopicBuilder.name("waiting-called-events")
                .partitions(3)
                .replicas(2)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .build();
    }
}
```

### 토픽 목록 조회

```bash
kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```java
Set<String> topicNames = adminClient.listTopics().names().get();
```

### 토픽 상세 조회

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic waiting-called-events
```

출력 예시:
```
Topic: waiting-called-events  PartitionCount: 3  ReplicationFactor: 2
  Partition: 0  Leader: 1  Replicas: 1,2  Isr: 1,2
  Partition: 1  Leader: 2  Replicas: 2,0  Isr: 2,0
  Partition: 2  Leader: 0  Replicas: 0,1  Isr: 0,1
```

```java
DescribeTopicsResult result = adminClient.describeTopics(List.of("waiting-called-events"));
TopicDescription desc = result.topicNameValues().get("waiting-called-events").get();

System.out.println("파티션 수: " + desc.partitions().size());
for (TopicPartitionInfo partition : desc.partitions()) {
    System.out.printf("Partition: %d, Leader: %d, ISR: %s%n",
        partition.partition(),
        partition.leader().id(),
        partition.isr().stream().map(Node::id).toList());
}
```

### 토픽 설정 변경

```bash
# 파티션 수 증가 (줄이기 불가)
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic waiting-called-events --partitions 6

# 토픽 설정 변경
kafka-configs.sh --bootstrap-server localhost:9092 \
  --alter --entity-type topics --entity-name waiting-called-events \
  --add-config retention.ms=259200000   # 3일로 변경
```

```java
// 파티션 수 증가
adminClient.createPartitions(
    Map.of("waiting-called-events", NewPartitions.increaseTo(6))
);

// 설정 변경
ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, "waiting-called-events");
AlterConfigOp op = new AlterConfigOp(
    new ConfigEntry("retention.ms", "259200000"),
    AlterConfigOp.OpType.SET
);
adminClient.incrementalAlterConfigs(Map.of(resource, List.of(op)));
```

### 토픽 삭제

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic waiting-called-events
```

```java
adminClient.deleteTopics(List.of("waiting-called-events"));
```

---

## 2. 메시지 발행 (Producer)

### 콘솔에서 메시지 발행

```bash
# 기본 발행
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events
> {"waitingId":1,"restaurantId":100,"status":"CALLED"}
> {"waitingId":2,"restaurantId":100,"status":"CALLED"}

# 키 지정 발행 (같은 키 → 같은 파티션)
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --property parse.key=true \
  --property key.separator=:
> 100:{"waitingId":1,"restaurantId":100,"status":"CALLED"}

# 헤더 포함 발행
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --property parse.headers=true \
  --property headers.delimiter=\t \
  --property headers.separator=,
> eventType:CALLED	{"waitingId":1}
```

```java
// Spring KafkaTemplate 사용
@RequiredArgsConstructor
@Service
public class WaitingEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    // 기본 발행
    public void send(String topic, String payload) {
        kafkaTemplate.send(topic, payload);
    }

    // 키 지정 발행
    public void sendWithKey(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload);
    }

    // 파티션 지정 발행
    public void sendToPartition(String topic, int partition, String key, String payload) {
        kafkaTemplate.send(topic, partition, key, payload);
    }

    // 헤더 포함 발행
    public void sendWithHeaders(String topic, String key, String payload, Map<String, String> headers) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        headers.forEach((k, v) -> record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);
    }

    // 콜백으로 발행 결과 확인
    public void sendWithCallback(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("발행 실패: topic={}, key={}", topic, key, ex);
                } else {
                    RecordMetadata metadata = result.getRecordMetadata();
                    log.info("발행 성공: topic={}, partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
    }
}
```

---

## 3. 메시지 소비 (Consumer)

### 콘솔에서 메시지 소비

```bash
# 처음부터 소비
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --from-beginning

# 특정 파티션, 특정 오프셋부터 소비
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --partition 0 \
  --offset 42

# 키, 타임스탬프 함께 출력
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true \
  --property print.headers=true

# 최대 N개만 소비하고 종료
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --from-beginning \
  --max-messages 10

# Consumer Group 지정
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic waiting-called-events \
  --group test-consumer-group
```

```java
// Spring @KafkaListener 사용
@Slf4j
@RequiredArgsConstructor
@Component
public class WaitingEventConsumer {

    // 기본 소비
    @KafkaListener(topics = "waiting-called-events", groupId = "notification-group")
    public void consume(String message) {
        log.info("수신: {}", message);
    }

    // 키, 헤더, 메타데이터 함께 수신
    @KafkaListener(topics = "waiting-called-events", groupId = "notification-group")
    public void consumeWithMetadata(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        log.info("수신: key={}, partition={}, offset={}, timestamp={}, payload={}",
            key, partition, offset, timestamp, payload);
    }

    // ConsumerRecord로 수신 (전체 정보)
    @KafkaListener(topics = "waiting-called-events", groupId = "notification-group")
    public void consumeRecord(ConsumerRecord<String, String> record) {
        log.info("topic={}, partition={}, offset={}, key={}, value={}",
            record.topic(), record.partition(), record.offset(),
            record.key(), record.value());
    }

    // 수동 ACK
    @KafkaListener(topics = "waiting-called-events", groupId = "notification-group")
    public void consumeManualAck(String message, Acknowledgment ack) {
        try {
            processMessage(message);
            ack.acknowledge();  // 처리 성공 시에만 커밋
        } catch (Exception e) {
            log.error("처리 실패, offset 커밋 안 함", e);
            // 다음 poll에서 재수신
        }
    }

    // 배치 소비
    @KafkaListener(topics = "waiting-called-events", groupId = "notification-group",
                   containerFactory = "batchKafkaListenerContainerFactory")
    public void consumeBatch(List<String> messages) {
        log.info("배치 수신: {} 건", messages.size());
        messages.forEach(this::processMessage);
    }

    // 특정 파티션만 소비
    @KafkaListener(topicPartitions = @TopicPartition(
        topic = "waiting-called-events",
        partitions = {"0", "1"}
    ))
    public void consumeFromPartitions(String message) {
        processMessage(message);
    }
}
```

---

## 4. Consumer Group 관리

### Consumer Group 목록

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
```

```java
ListConsumerGroupsResult result = adminClient.listConsumerGroups();
result.all().get().forEach(group ->
    System.out.println(group.groupId() + " [" + group.state().orElse(null) + "]")
);
```

### Consumer Group 상세 (Lag 확인)

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group notification-group
```

출력 예시:
```
GROUP              TOPIC                   PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
notification-group waiting-called-events   0          150             155             5
notification-group waiting-called-events   1          200             200             0
notification-group waiting-called-events   2          180             190             10
```

```java
// Consumer Group 상세 조회 + Lag 계산
public Map<TopicPartition, Long> getConsumerLag(String groupId) throws Exception {
    // 1. 현재 커밋된 오프셋
    Map<TopicPartition, OffsetAndMetadata> offsets =
        adminClient.listConsumerGroupOffsets(groupId)
            .partitionsToOffsetAndMetadata().get();

    // 2. 각 파티션의 최신 오프셋 (log-end-offset)
    Map<TopicPartition, OffsetSpec> latestRequest = offsets.keySet().stream()
        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
        adminClient.listOffsets(latestRequest).all().get();

    // 3. Lag = log-end-offset - current-offset
    Map<TopicPartition, Long> lag = new HashMap<>();
    offsets.forEach((tp, offsetMeta) -> {
        long endOffset = endOffsets.get(tp).offset();
        lag.put(tp, endOffset - offsetMeta.offset());
    });
    return lag;
}
```

### Offset 리셋

```bash
# 처음부터 다시 소비
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-called-events \
  --reset-offsets --to-earliest --execute

# 최신 오프셋으로 이동 (밀린 메시지 스킵)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-called-events \
  --reset-offsets --to-latest --execute

# 특정 오프셋으로 이동
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-called-events:0 \
  --reset-offsets --to-offset 100 --execute

# 특정 시간 기준으로 이동
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-called-events \
  --reset-offsets --to-datetime "2025-01-01T00:00:00.000" --execute

# 현재보다 N만큼 뒤로 이동
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-called-events \
  --reset-offsets --shift-by -10 --execute
```

```java
// Offset 리셋 (AdminClient)
public void resetToEarliest(String groupId, String topic) throws Exception {
    // Consumer Group이 멈춘 상태에서만 가능
    Map<TopicPartition, OffsetSpec> earliestRequest = Map.of(
        new TopicPartition(topic, 0), OffsetSpec.earliest(),
        new TopicPartition(topic, 1), OffsetSpec.earliest(),
        new TopicPartition(topic, 2), OffsetSpec.earliest()
    );

    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
        adminClient.listOffsets(earliestRequest).all().get();

    Map<TopicPartition, OffsetAndMetadata> resetOffsets = offsets.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new OffsetAndMetadata(e.getValue().offset())
        ));

    adminClient.alterConsumerGroupOffsets(groupId, resetOffsets).all().get();
}

// KafkaListener에서 시작 오프셋 제어
@KafkaListener(topics = "waiting-called-events", groupId = "reprocess-group",
               properties = "auto.offset.reset=earliest")
public void reprocessFromBeginning(String message) {
    // 새로운 groupId로 처음부터 다시 소비
}
```

### Consumer Group 삭제

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --delete --group notification-group
```

```java
adminClient.deleteConsumerGroups(List.of("notification-group")).all().get();
```

---

## 5. 메시지 조회/디버깅

### 특정 토픽의 메시지 수 확인

```bash
# 각 파티션의 earliest/latest offset
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic waiting-called-events \
  --time -1    # -1: latest, -2: earliest
```

```java
Map<TopicPartition, OffsetSpec> request = Map.of(
    new TopicPartition("waiting-called-events", 0), OffsetSpec.latest(),
    new TopicPartition("waiting-called-events", 0), OffsetSpec.earliest()
);
Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> result =
    adminClient.listOffsets(request).all().get();
// latest - earliest = 해당 파티션의 메시지 수 (대략)
```

### 토픽 레코드 삭제

```bash
# delete-records.json: {"partitions":[{"topic":"t","partition":0,"offset":100}],"version":1}
kafka-delete-records.sh --bootstrap-server localhost:9092 \
  --offset-json-file delete-records.json
```

```java
// 특정 오프셋 이전 레코드 삭제
Map<TopicPartition, RecordsToDelete> toDelete = Map.of(
    new TopicPartition("waiting-called-events", 0), RecordsToDelete.beforeOffset(100)
);
adminClient.deleteRecords(toDelete).all().get();
```

---

## 6. 클러스터 관리

### 브로커 목록 확인

```bash
kafka-broker-api-versions.sh --bootstrap-server localhost:9092 | head -1

# 또는 메타데이터 조회
kafka-metadata.sh --snapshot /var/kafka-logs/__cluster_metadata-0/00000000000000000000.log
```

```java
DescribeClusterResult cluster = adminClient.describeCluster();
System.out.println("Cluster ID: " + cluster.clusterId().get());
System.out.println("Controller: " + cluster.controller().get());
cluster.nodes().get().forEach(node ->
    System.out.printf("Broker: id=%d, host=%s, port=%d%n", node.id(), node.host(), node.port())
);
```

### Under-Replicated Partitions 확인

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --under-replicated-partitions
```

```java
DescribeTopicsResult result = adminClient.describeTopics(topicNames);
result.allTopicNames().get().forEach((name, desc) -> {
    desc.partitions().forEach(p -> {
        if (p.isr().size() < p.replicas().size()) {
            System.out.printf("Under-replicated: %s-%d (ISR: %d/%d)%n",
                name, p.partition(), p.isr().size(), p.replicas().size());
        }
    });
});
```

### Preferred Leader Election

```bash
kafka-leader-election.sh --bootstrap-server localhost:9092 \
  --election-type PREFERRED \
  --all-topic-partitions
```

```java
adminClient.electLeaders(
    ElectionType.PREFERRED,
    null  // null = 모든 파티션
).all().get();
```

---

## 7. AdminClient Bean 설정

```java
@Configuration
public class KafkaAdminConfig {

    @Bean
    public AdminClient adminClient(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000
        ));
    }
}
```

---

---

# Part 2. Redis 명령어

## 1. 문자열 (String)

캐시, 세션, 카운터 등 가장 기본적인 자료구조.

### SET / GET

```bash
# 값 저장
SET user:1:name "홍길동"

# TTL과 함께 저장
SET session:abc123 "{\"userId\":1}" EX 3600    # 3600초 후 만료
SET session:abc123 "{\"userId\":1}" PX 60000   # 60000밀리초 후 만료

# 키가 없을 때만 저장 (분산 락에 활용)
SET lock:resource:1 "owner-1" NX EX 30

# 값 조회
GET user:1:name
# "홍길동"

# 여러 키 동시 조회
MGET user:1:name user:2:name user:3:name

# 여러 키 동시 저장
MSET user:1:name "홍길동" user:2:name "김철수"
```

```java
@RequiredArgsConstructor
@Service
public class RedisStringService {

    private final StringRedisTemplate redisTemplate;

    // SET
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // SET with TTL
    public void setWithTtl(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // SETNX (키가 없을 때만 저장)
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, value, ttl)
        );
    }

    // GET
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // MGET
    public List<String> multiGet(List<String> keys) {
        return redisTemplate.opsForValue().multiGet(keys);
    }

    // MSET
    public void multiSet(Map<String, String> map) {
        redisTemplate.opsForValue().multiSet(map);
    }
}
```

### INCR / DECR (카운터)

```bash
# 1 증가
INCR waiting:count:restaurant:100
# (integer) 1

# N 증가
INCRBY waiting:count:restaurant:100 5
# (integer) 6

# 1 감소
DECR waiting:count:restaurant:100

# 실수 증가
INCRBYFLOAT product:price:1 2.5
```

```java
// INCR
Long count = redisTemplate.opsForValue().increment("waiting:count:restaurant:100");

// INCRBY
Long count = redisTemplate.opsForValue().increment("waiting:count:restaurant:100", 5);

// DECR
Long count = redisTemplate.opsForValue().decrement("waiting:count:restaurant:100");

// INCRBYFLOAT
Double price = redisTemplate.opsForValue().increment("product:price:1", 2.5);
```

---

## 2. 해시 (Hash)

객체의 필드별 저장/조회. 엔티티 캐싱에 적합.

```bash
# 필드 저장
HSET restaurant:100 name "맛집" address "서울시 강남구" capacity 50

# 필드 조회
HGET restaurant:100 name
# "맛집"

# 전체 필드 조회
HGETALL restaurant:100
# 1) "name"       2) "맛집"
# 3) "address"    4) "서울시 강남구"
# 5) "capacity"   6) "50"

# 여러 필드 조회
HMGET restaurant:100 name capacity

# 필드 존재 확인
HEXISTS restaurant:100 name
# (integer) 1

# 필드 삭제
HDEL restaurant:100 address

# 필드 수 조회
HLEN restaurant:100

# 숫자 필드 증가
HINCRBY restaurant:100 capacity 10
```

```java
@RequiredArgsConstructor
@Service
public class RedisHashService {

    private final StringRedisTemplate redisTemplate;

    // HSET (단일 필드)
    public void hset(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    // HSET (여러 필드)
    public void hsetAll(String key, Map<String, String> fields) {
        redisTemplate.opsForHash().putAll(key, fields);
    }

    // HGET
    public Object hget(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    // HGETALL
    public Map<Object, Object> hgetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    // HMGET
    public List<Object> hmget(String key, List<String> fields) {
        return redisTemplate.opsForHash().multiGet(key, List.copyOf(fields));
    }

    // HDEL
    public Long hdel(String key, String... fields) {
        return redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    // HINCRBY
    public Long hincrBy(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    // HEXISTS
    public boolean hexists(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }
}
```

---

## 3. 리스트 (List)

큐(FIFO), 스택(LIFO), 최근 항목 관리에 사용.

```bash
# 왼쪽에 추가 (LPUSH) - 스택/큐 입구
LPUSH notifications:user:1 "웨이팅 호출되었습니다"
LPUSH notifications:user:1 "주문이 접수되었습니다"

# 오른쪽에 추가 (RPUSH)
RPUSH queue:tasks "task-1" "task-2" "task-3"

# 왼쪽에서 꺼내기 (LPOP) - 큐 출구
LPOP queue:tasks
# "task-1"

# 오른쪽에서 꺼내기 (RPOP) - 스택 출구
RPOP notifications:user:1

# 범위 조회 (인덱스 기반, 0부터 시작, -1은 마지막)
LRANGE notifications:user:1 0 -1    # 전체
LRANGE notifications:user:1 0 9     # 최근 10개

# 리스트 길이
LLEN queue:tasks

# 특정 인덱스 값
LINDEX notifications:user:1 0

# 블로킹 팝 (큐로 사용 시, 타임아웃 5초)
BLPOP queue:tasks 5

# 리스트 크기 제한 (최근 100개만 유지)
LTRIM notifications:user:1 0 99
```

```java
@RequiredArgsConstructor
@Service
public class RedisListService {

    private final StringRedisTemplate redisTemplate;

    // LPUSH
    public Long lpush(String key, String value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    // RPUSH
    public Long rpush(String key, String value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    // LPOP
    public String lpop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    // RPOP
    public String rpop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    // BLPOP (블로킹)
    public String blpop(String key, Duration timeout) {
        return redisTemplate.opsForList().leftPop(key, timeout);
    }

    // LRANGE
    public List<String> lrange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    // LLEN
    public Long llen(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // LPUSH + LTRIM (최근 N개만 유지)
    public void addWithLimit(String key, String value, long limit) {
        redisTemplate.opsForList().leftPush(key, value);
        redisTemplate.opsForList().trim(key, 0, limit - 1);
    }
}
```

---

## 4. 셋 (Set)

중복 없는 집합. 태그, 좋아요 유저 목록 등에 사용.

```bash
# 추가
SADD restaurant:100:tags "한식" "맛집" "강남"

# 멤버 확인
SISMEMBER restaurant:100:tags "한식"
# (integer) 1

# 전체 조회
SMEMBERS restaurant:100:tags

# 멤버 수
SCARD restaurant:100:tags

# 삭제
SREM restaurant:100:tags "강남"

# 랜덤 조회
SRANDMEMBER restaurant:100:tags 2

# 집합 연산
SINTER restaurant:100:tags restaurant:200:tags     # 교집합
SUNION restaurant:100:tags restaurant:200:tags     # 합집합
SDIFF restaurant:100:tags restaurant:200:tags      # 차집합
```

```java
@RequiredArgsConstructor
@Service
public class RedisSetService {

    private final StringRedisTemplate redisTemplate;

    // SADD
    public Long sadd(String key, String... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    // SISMEMBER
    public boolean sismember(String key, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    // SMEMBERS
    public Set<String> smembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    // SCARD
    public Long scard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // SREM
    public Long srem(String key, String... values) {
        return redisTemplate.opsForSet().remove(key, (Object[]) values);
    }

    // SINTER
    public Set<String> sinter(String key1, String key2) {
        return redisTemplate.opsForSet().intersect(key1, key2);
    }

    // SUNION
    public Set<String> sunion(String key1, String key2) {
        return redisTemplate.opsForSet().union(key1, key2);
    }

    // SDIFF
    public Set<String> sdiff(String key1, String key2) {
        return redisTemplate.opsForSet().difference(key1, key2);
    }
}
```

---

## 5. 정렬된 셋 (Sorted Set / ZSet)

**Booster 프로젝트의 대기열 핵심 자료구조.** 점수(score) 기준 정렬. 랭킹, 대기 순번에 사용.

```bash
# 추가 (score = 등록 타임스탬프)
ZADD waiting:queue:100 1706500000 "waiting:1"
ZADD waiting:queue:100 1706500010 "waiting:2"
ZADD waiting:queue:100 1706500020 "waiting:3"

# 순위 조회 (0부터 시작, score 오름차순)
ZRANK waiting:queue:100 "waiting:2"
# (integer) 1  → 0번째부터이므로 대기 순번 = ZRANK + 1

# 역순 순위
ZREVRANK waiting:queue:100 "waiting:2"

# 점수 조회
ZSCORE waiting:queue:100 "waiting:1"
# "1706500000"

# 범위 조회 (인덱스 기반)
ZRANGE waiting:queue:100 0 -1                    # 전체 (오름차순)
ZRANGE waiting:queue:100 0 -1 WITHSCORES         # 점수 포함
ZREVRANGE waiting:queue:100 0 2                   # 상위 3개 (내림차순)

# 범위 조회 (점수 기반)
ZRANGEBYSCORE waiting:queue:100 1706500000 1706500015
# 1) "waiting:1"  2) "waiting:2"

# 멤버 삭제 (호출 완료 시)
ZREM waiting:queue:100 "waiting:1"

# 멤버 수
ZCARD waiting:queue:100

# 점수 범위 내 멤버 수
ZCOUNT waiting:queue:100 1706500000 1706500020

# 점수 증가
ZINCRBY leaderboard 10 "player:1"
```

```java
@RequiredArgsConstructor
@Service
public class RedisZSetService {

    private final StringRedisTemplate redisTemplate;

    // ZADD - 대기열 등록
    public Boolean zadd(String key, String value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    // ZRANK - 대기 순번 조회
    public Long zrank(String key, String value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    // 대기 순번 (1부터 시작)
    public Long getWaitingPosition(Long restaurantId, Long waitingId) {
        Long rank = redisTemplate.opsForZSet()
            .rank("waiting:queue:" + restaurantId, String.valueOf(waitingId));
        return rank != null ? rank + 1 : null;
    }

    // ZSCORE
    public Double zscore(String key, String value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    // ZRANGE (인덱스 범위)
    public Set<String> zrange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    // ZRANGE WITHSCORES
    public Set<ZSetOperations.TypedTuple<String>> zrangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    // ZRANGEBYSCORE (점수 범위)
    public Set<String> zrangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    // ZREM - 대기열에서 제거
    public Long zrem(String key, String... values) {
        return redisTemplate.opsForZSet().remove(key, (Object[]) values);
    }

    // ZCARD - 대기열 크기
    public Long zcard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    // ZCOUNT
    public Long zcount(String key, double min, double max) {
        return redisTemplate.opsForZSet().count(key, min, max);
    }

    // ZINCRBY
    public Double zincrby(String key, String value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }
}

// Redisson 사용 (Booster 프로젝트)
@RequiredArgsConstructor
@Service
public class WaitingQueueRedissonService {

    private final RedissonClient redissonClient;

    public void addToQueue(Long restaurantId, Long waitingId, long timestamp) {
        RScoredSortedSet<String> queue = redissonClient
            .getScoredSortedSet("waiting:queue:" + restaurantId);
        queue.add(timestamp, String.valueOf(waitingId));
    }

    public Integer getPosition(Long restaurantId, Long waitingId) {
        RScoredSortedSet<String> queue = redissonClient
            .getScoredSortedSet("waiting:queue:" + restaurantId);
        Integer rank = queue.rank(String.valueOf(waitingId));
        return rank != null ? rank + 1 : null;
    }

    public void removeFromQueue(Long restaurantId, Long waitingId) {
        RScoredSortedSet<String> queue = redissonClient
            .getScoredSortedSet("waiting:queue:" + restaurantId);
        queue.remove(String.valueOf(waitingId));
    }
}
```

---

## 6. 키 관리

```bash
# 키 존재 확인
EXISTS user:1:name
# (integer) 1

# 키 삭제
DEL user:1:name

# 비동기 삭제 (Big Key는 반드시 이것 사용)
UNLINK large:hash:key

# TTL 설정
EXPIRE session:abc123 3600        # 3600초
PEXPIRE session:abc123 60000      # 60000밀리초
EXPIREAT session:abc123 1706500000  # Unix timestamp

# TTL 확인
TTL session:abc123                # 초 단위 (-1: 만료 없음, -2: 키 없음)
PTTL session:abc123               # 밀리초 단위

# TTL 제거 (영구 키로 변경)
PERSIST session:abc123

# 키 타입 확인
TYPE waiting:queue:100
# zset

# 키 이름 변경
RENAME old:key new:key

# 키 검색 (운영 금지! SCAN 사용)
# KEYS waiting:*            ← 절대 사용 금지
SCAN 0 MATCH waiting:* COUNT 100
```

```java
@RequiredArgsConstructor
@Service
public class RedisKeyService {

    private final StringRedisTemplate redisTemplate;

    // EXISTS
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // DEL
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    // DEL (여러 키)
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // UNLINK (비동기 삭제)
    public Long unlink(String key) {
        return redisTemplate.unlink(key);
    }

    // EXPIRE
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
    }

    // TTL
    public Long ttl(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    // TYPE
    public DataType type(String key) {
        return redisTemplate.type(key);
    }

    // SCAN (KEYS 대신 사용)
    public Set<String> scan(String pattern, long count) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(count).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
```

---

## 7. Pub/Sub

서버 간 메시지 브로드캐스트. WebSocket 멀티 서버 환경에서 활용.

```bash
# 채널 구독 (터미널 1)
SUBSCRIBE waiting:notifications:restaurant:100

# 패턴 구독
PSUBSCRIBE waiting:notifications:*

# 메시지 발행 (터미널 2)
PUBLISH waiting:notifications:restaurant:100 "웨이팅 3번 호출"
# (integer) 1   ← 수신한 구독자 수

# 활성 채널 확인
PUBSUB CHANNELS waiting:*

# 채널별 구독자 수
PUBSUB NUMSUB waiting:notifications:restaurant:100
```

```java
// Publisher
@RequiredArgsConstructor
@Service
public class RedisPubService {

    private final StringRedisTemplate redisTemplate;

    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}

// Subscriber (MessageListener 방식)
@Configuration
public class RedisSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WaitingNotificationListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 특정 채널 구독
        container.addMessageListener(listener,
            new ChannelTopic("waiting:notifications:restaurant:100"));
        // 패턴 구독
        container.addMessageListener(listener,
            new PatternTopic("waiting:notifications:*"));
        return container;
    }
}

@Slf4j
@Component
public class WaitingNotificationListener implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        log.info("수신: channel={}, message={}", channel, body);
    }
}
```

---

## 8. 트랜잭션 / Pipeline

### MULTI-EXEC (트랜잭션)

```bash
MULTI
SET user:1:name "홍길동"
INCR user:1:loginCount
EXPIRE user:1:name 3600
EXEC
```

```java
// RedisTemplate 트랜잭션
List<Object> results = redisTemplate.execute(new SessionCallback<>() {
    @Override
    public List<Object> execute(RedisOperations operations) {
        operations.multi();
        operations.opsForValue().set("user:1:name", "홍길동");
        operations.opsForValue().increment("user:1:loginCount");
        operations.expire("user:1:name", Duration.ofHours(1));
        return operations.exec();
    }
});
```

### Pipeline (네트워크 왕복 최소화)

```bash
# redis-cli에서는 --pipe 옵션
echo -e "SET key1 val1\nSET key2 val2\nGET key1" | redis-cli --pipe
```

```java
// Pipeline으로 대량 명령 실행 (RTT 절감)
List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
    @Override
    public Object execute(RedisOperations operations) {
        for (int i = 0; i < 1000; i++) {
            operations.opsForValue().set("key:" + i, "value:" + i);
        }
        return null;  // pipeline에서는 반환값 무시
    }
});
```

---

## 9. Lua 스크립트

원자적 복합 연산. 여러 명령을 하나의 원자적 작업으로 실행.

```bash
# 조건부 증가: 현재 값이 limit 미만일 때만 INCR
EVAL "local current = tonumber(redis.call('GET', KEYS[1]) or 0) \
      if current < tonumber(ARGV[1]) then \
        return redis.call('INCR', KEYS[1]) \
      else \
        return -1 \
      end" 1 waiting:count:restaurant:100 50
```

```java
// Lua 스크립트로 원자적 대기열 등록 (중복 방지 + 인원 제한)
@Service
public class WaitingQueueAtomicService {

    private static final RedisScript<Long> ADD_IF_NOT_EXISTS_AND_UNDER_LIMIT = new DefaultRedisScript<>("""
        local queueKey = KEYS[1]
        local member = ARGV[1]
        local score = ARGV[2]
        local limit = tonumber(ARGV[3])

        -- 이미 대기열에 있는지 확인
        if redis.call('ZSCORE', queueKey, member) then
            return -1  -- 이미 등록됨
        end

        -- 대기열 인원 제한 확인
        local currentSize = redis.call('ZCARD', queueKey)
        if currentSize >= limit then
            return -2  -- 인원 초과
        end

        -- 등록
        redis.call('ZADD', queueKey, score, member)
        return redis.call('ZRANK', queueKey, member)
        """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public Long addToQueueAtomically(Long restaurantId, Long waitingId, long timestamp, long limit) {
        return redisTemplate.execute(
            ADD_IF_NOT_EXISTS_AND_UNDER_LIMIT,
            List.of("waiting:queue:" + restaurantId),
            String.valueOf(waitingId),
            String.valueOf(timestamp),
            String.valueOf(limit)
        );
        // -1: 이미 등록, -2: 인원 초과, 0+: 성공 (순번)
    }
}
```

---

## 10. 서버 관리/디버깅

```bash
# 서버 정보 (전체)
INFO

# 섹션별 조회
INFO memory              # 메모리 상태
INFO clients             # 연결된 클라이언트
INFO stats               # 통계
INFO replication         # 복제 상태
INFO keyspace            # DB별 키 수

# 실시간 모니터링
MONITOR                  # 모든 명령 실시간 출력 (디버깅용, 운영 주의)

# Slow Log
SLOWLOG GET 10           # 최근 느린 쿼리 10개
SLOWLOG LEN              # Slow Log 개수
SLOWLOG RESET            # 초기화

# 클라이언트 관리
CLIENT LIST              # 연결된 클라이언트 목록
CLIENT KILL ID 123       # 특정 클라이언트 연결 종료
CLIENT GETNAME           # 현재 클라이언트 이름
CLIENT SETNAME myapp     # 클라이언트 이름 설정 (디버깅 편의)

# DB 크기
DBSIZE                   # 현재 DB의 키 수

# 설정 확인/변경
CONFIG GET maxmemory
CONFIG SET maxmemory 4gb
CONFIG GET maxmemory-policy

# 메모리 분석
MEMORY USAGE key:name          # 특정 키 메모리 사용량
MEMORY DOCTOR                  # 메모리 진단

# 레이턴시 진단
LATENCY LATEST                 # 최근 레이턴시 이벤트
LATENCY HISTORY event-name     # 특정 이벤트 히스토리

# 영속성
BGSAVE                         # 백그라운드 RDB 스냅샷
BGREWRITEAOF                   # 백그라운드 AOF 재작성
LASTSAVE                       # 마지막 RDB 저장 시간
```

```java
// Spring RedisTemplate으로 서버 관리 명령 실행
@RequiredArgsConstructor
@Service
public class RedisAdminService {

    private final StringRedisTemplate redisTemplate;

    // INFO
    public Properties getInfo(String section) {
        return redisTemplate.getConnectionFactory()
            .getConnection().serverCommands().info(section);
    }

    // DBSIZE
    public Long dbSize() {
        return redisTemplate.getConnectionFactory()
            .getConnection().serverCommands().dbSize();
    }

    // CONFIG GET
    public Properties getConfig(String pattern) {
        return redisTemplate.getConnectionFactory()
            .getConnection().serverCommands().getConfig(pattern);
    }

    // MEMORY USAGE
    public Long memoryUsage(String key) {
        return redisTemplate.execute((RedisCallback<Long>) connection ->
            connection.serverCommands().execute("MEMORY",
                "USAGE".getBytes(), key.getBytes())
        );
    }
}
```

---

## 빠른 참조표

### Kafka CLI → Java 매핑

| 작업 | CLI 명령 | Java (Spring) |
|------|----------|---------------|
| 토픽 생성 | `kafka-topics.sh --create` | `TopicBuilder.name().build()` 또는 `AdminClient.createTopics()` |
| 토픽 목록 | `kafka-topics.sh --list` | `AdminClient.listTopics()` |
| 토픽 상세 | `kafka-topics.sh --describe` | `AdminClient.describeTopics()` |
| 메시지 발행 | `kafka-console-producer.sh` | `KafkaTemplate.send()` |
| 메시지 소비 | `kafka-console-consumer.sh` | `@KafkaListener` |
| Lag 확인 | `kafka-consumer-groups.sh --describe` | `AdminClient.listConsumerGroupOffsets()` |
| Offset 리셋 | `kafka-consumer-groups.sh --reset-offsets` | `AdminClient.alterConsumerGroupOffsets()` |

### Redis CLI → Java 매핑

| 작업 | CLI 명령 | Java (Spring) |
|------|----------|---------------|
| 문자열 저장 | `SET` | `opsForValue().set()` |
| 문자열 조회 | `GET` | `opsForValue().get()` |
| 해시 저장 | `HSET` | `opsForHash().put()` |
| 해시 조회 | `HGETALL` | `opsForHash().entries()` |
| 리스트 추가 | `LPUSH/RPUSH` | `opsForList().leftPush()/rightPush()` |
| 셋 추가 | `SADD` | `opsForSet().add()` |
| ZSet 추가 | `ZADD` | `opsForZSet().add()` |
| ZSet 순위 | `ZRANK` | `opsForZSet().rank()` |
| 키 삭제 | `DEL/UNLINK` | `delete()/unlink()` |
| TTL 설정 | `EXPIRE` | `expire()` |
| 키 검색 | `SCAN` | `scan(ScanOptions)` |
| Pub/Sub | `PUBLISH/SUBSCRIBE` | `convertAndSend()` / `MessageListener` |
| Pipeline | `--pipe` | `executePipelined()` |
| Lua 스크립트 | `EVAL` | `execute(RedisScript)` |