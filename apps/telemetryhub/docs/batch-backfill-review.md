# TelemetryHub Batch Backfill Review

## 업데이트 2026-04-24

현재 구현은 더 이상 dry-run 골격만 있는 상태가 아니다.

### 현재 source read 모델

backfill source loading은 이제 전체 source를 먼저 메모리에 올리는 방식이 아니라 chunk 중심 방식이다.

`BackfillSourceReader`는 이제 chunk 전달 의미를 가진다.

- source reader가 전체 `List<BackfillRawEvent>`를 반환할 필요가 없고
- job은 source를 chunk 단위로 소비하며
- 각 chunk는 즉시 target writer로 전달된다

구현 위치는 아래와 같다.

- `BackfillSourceReader.readChunks(...)`
- `FileBackfillSourceReader`
- `RoutingBackfillSourceReader`
- `BatchBackfillJobConfig`

### 현재 NDJSON 파일 처리 방식

`FileBackfillSourceReader`는 이제:

- NDJSON을 line 단위로 읽고
- 읽는 동안 plan 시간 범위로 필터링하며
- `plan.chunkSize()` 기준으로 chunk를 내보내고
- 전체 파일을 한 번에 메모리에 물질화하지 않는다

그래서 큰 replay source에서도 이전의 full-file 메모리 압박 위험이 줄었다.

### 현재 raw event 모델

`BackfillRawEvent`에는 이제 `ingestTime`이 포함된다.

이 변경이 중요한 이유는:

- `device_last_seen.last_ingest_time`에 실제 backfill 이벤트 메타데이터가 기록되고
- replay 데이터가 더 이상 `eventTime`을 ingest-time 필드에 중복 기록하지 않기 때문이다

### 현재 `device_last_seen` write 규칙

`DeviceLastSeenBackfillWriter`는 이제:

- `select count(*)` 후 insert/update 분기 대신 직접 insert/upsert를 사용하고
- `SKIP_EXISTING`을 유지하며
- 오래된 backfill 데이터가 최신 row를 덮지 못하게 한다

현재 overwrite guard:

- 더 최신 `eventTime`이 우선
- eventTime이 같으면 더 최신 `ingestTime`이 우선

### 현재 실행 모델

batch job은 이제 아래 순서로 동작한다.

1. plan 준비
2. source를 chunk 단위로 읽기
3. chunk 단위로 target writer 실행
4. 전체 read/write 수 누적
5. execution snapshot 기록

즉 예전의 full-list 방식보다 실제 replay 흐름에 훨씬 가까워졌다.

## 목적
`batch-backfill`은 실시간 처리로 놓친 late event 보정, 산식 변경 이후 재계산, 과거 기간 rebuild를 담당하는 배치 모듈이다.

현재 구현은 실제 raw replay 이전 단계의 안전한 골격이다. 핵심은 바로 데이터를 다시 쓰는 것이 아니라, `무엇을 어떤 규칙으로 다시 계산할지`를 먼저 고정하는 데 있다.

## 현재 구조
- [BatchBackfillApplication.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/BatchBackfillApplication.java)
  - 애플리케이션 시작점
- [BatchBackfillProperties.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/config/BatchBackfillProperties.java)
  - 기본 backfill 범위와 overwrite 정책
- [DefaultBackfillPlanFactory.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/application/DefaultBackfillPlanFactory.java)
  - 기본 backfill plan 생성
- [BackfillPlanService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/application/BackfillPlanService.java)
  - 현재 기준 plan 준비와 snapshot 보관
- [BatchBackfillJobConfig.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/BatchBackfillJobConfig.java)
  - Spring Batch job / step 골격
- [StubBackfillSourceReader.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/StubBackfillSourceReader.java)
  - 실제 raw source 대신 dry-run용 샘플 이벤트 공급
- [FileBackfillSourceReader.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/FileBackfillSourceReader.java)
  - NDJSON 파일 기반 실제 raw source reader
- [RoutingBackfillSourceReader.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/RoutingBackfillSourceReader.java)
  - 실제 file reader와 stub fallback 사이를 라우팅
- [RoutingBackfillTargetWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/RoutingBackfillTargetWriter.java)
  - target별 write 라우팅 경계
- `DeviceLastSeenBackfillWriter`
- `EventsPerMinuteBackfillWriter`
- `DrivingEventCounterBackfillWriter`
- `RegionHeatmapBackfillWriter`
  - target별 실제 재계산 writer

## 현재 plan 모델
현재 backfill은 아래 기준을 먼저 잡고 있다.

- source type
- `RAW_TOPIC_EXPORT`
- `RAW_ARCHIVE`
- overwrite mode
  - `MERGE`
  - `OVERWRITE`
  - `SKIP_EXISTING`
- target
  - `DEVICE_LAST_SEEN`
  - `EVENTS_PER_MINUTE`
  - `DRIVING_EVENT_COUNTER`
  - `REGION_HEATMAP`

즉 현재 단계에서는 “어디서 읽고”, “무엇을 다시 만들고”, “기존 결과와 어떻게 합칠지”를 코드로 고정한 상태다.

## 왜 dry-run 중심으로 시작했는가
backfill은 실시간 처리보다 위험하다.

- 같은 read model을 다시 갱신한다
- 실시간 처리와 write 충돌이 날 수 있다
- overwrite 기준이 모호하면 운영 데이터가 꼬일 수 있다

그래서 지금은 실제 replay보다 먼저:
- 기본 기간 설정
- target 목록
- overwrite 정책
- source 경로

이 네 가지를 안전하게 잡는 쪽으로 시작했다.

## 현재 상태
현재는 `spring.batch.job.enabled=false` 상태라 애플리케이션이 기동될 때 자동 실행되지는 않는다.

`telemetryhubBackfillJob`은 준비되어 있고, 현재는 아래 두 단계를 가진다.

- plan 준비 step
- dry-run replay step

현재 replay는 우선 실제 NDJSON 파일 source를 읽으려 시도하고, 파일이 없을 때만 stub source로 fallback 한다.

파일 포맷은 한 줄당 한 이벤트인 NDJSON 기준이다. 각 줄은 최소 아래 필드를 포함하면 된다.

```json
{"eventType":"TELEMETRY","eventId":"evt-1","deviceId":"device-001","eventTime":"2026-04-24T10:00:00Z","payload":"{\"lat\":37.5,...}"}
```

설정 프로퍼티:
- `telemetryhub.backfill.raw-topic-export-path`
- `telemetryhub.backfill.raw-archive-path`
- `telemetryhub.backfill.fallback-to-stub-when-source-missing`
- `telemetryhub.backfill.heatmap-grid-size`

## 현재 target writer 동작
- `DEVICE_LAST_SEEN`
  - device별 최신 event 기준으로 upsert
- `EVENTS_PER_MINUTE`
  - `eventType + minuteBucket` 기준 재집계 후 upsert
- `DRIVING_EVENT_COUNTER`
  - `deviceId + drivingEventType + minuteBucket` 기준 재집계 후 upsert
- `REGION_HEATMAP`
  - telemetry payload의 `lat/lon`을 grid bucket으로 내린 뒤 재집계 후 upsert

`OVERWRITE`는 시간 버킷 기반 테이블에서는 해당 범위를 먼저 삭제한 뒤 다시 적재한다.  
`DEVICE_LAST_SEEN`은 전역 latest 특성 때문에 delete 없이 upsert 로만 처리한다.

## 다음 단계
- target별 processor / writer 세분화
- overwrite / merge / skip 정책 실제 구현
- replay checkpoint 설계
- 실시간 stream-processor와 write 충돌 방지 규칙 정리
