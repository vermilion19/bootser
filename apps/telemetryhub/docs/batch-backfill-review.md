# TelemetryHub Batch Backfill Review

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

`telemetryhubBackfillJob`은 준비되어 있지만, 지금은 기본 plan을 만들고 로그로 남기는 준비 단계다.

## 다음 단계
- raw source reader 추가
- target별 processor / writer 분리
- overwrite / merge / skip 정책 실제 구현
- replay checkpoint 설계
- 실시간 stream-processor와 write 충돌 방지 규칙 정리
