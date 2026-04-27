# TelemetryHub Batch Backfill Review

## 목적
`batch-backfill`은 실시간 처리만으로 놓친 late event 보정, 과거 기간 재집계, 스키마 변경 후 rebuild를 담당하는 배치 모듈이다.

## 현재 패키지 구조
```text
batchbackfill
├─ application
│  ├─ execution
│  ├─ io
│  └─ plan
├─ config
├─ domain
└─ infrastructure
   ├─ job
   ├─ source
   └─ target
```

## 패키지별 역할
| 패키지 | 역할 |
| --- | --- |
| `application.execution` | 마지막 실행 결과와 메트릭 모델 |
| `application.io` | source reader / target writer 포트와 raw event 모델 |
| `application.plan` | backfill 범위와 실행 계획 정의 |
| `config` | source 경로, chunk size, dry-run 기본값 설정 |
| `domain` | source type, overwrite mode, target enum |
| `infrastructure.job` | Spring Batch job/step 구성 |
| `infrastructure.source` | file/stub source 구현과 라우팅 |
| `infrastructure.target` | dry-run 및 실제 target writer 구현과 라우팅 |

## 패키지별 클래스 표

### `application.execution`
| 클래스 | 역할 |
| --- | --- |
| `BackfillExecutionMetricsSnapshot` | 실행 메트릭 스냅샷이다. |
| `BackfillExecutionSnapshot` | 마지막 실행 결과 요약이다. |

### `application.io`
| 클래스 | 역할 |
| --- | --- |
| `BackfillRawEvent` | replay 대상 raw event 모델이다. |
| `BackfillSourceReader` | backfill source 읽기 포트다. |
| `BackfillTargetWriter` | backfill target 쓰기 포트다. |

### `application.plan`
| 클래스 | 역할 |
| --- | --- |
| `BackfillPlan` | 기간, source, target, overwrite mode를 포함한 실행 계획이다. |
| `BackfillPlanService` | 현재 설정 기준으로 backfill plan을 계산한다. |
| `DefaultBackfillPlanFactory` | 기본 backfill plan 생성 구현이다. |

### `config`
| 클래스 | 역할 |
| --- | --- |
| `BatchBackfillProperties` | chunk size, source path, fallback 옵션 등을 담는다. |

### `domain`
| 클래스 | 역할 |
| --- | --- |
| `BackfillOverwriteMode` | overwrite 전략 enum |
| `BackfillSourceType` | source 유형 enum |
| `BackfillTarget` | 재계산 대상 enum |

### `infrastructure.job`
| 클래스 | 역할 |
| --- | --- |
| `BatchBackfillJobConfig` | Spring Batch job과 step을 구성한다. |

### `infrastructure.source`
| 클래스 | 역할 |
| --- | --- |
| `FileBackfillRawEventRecord` | 파일 입력 한 줄을 매핑하는 모델이다. |
| `FileBackfillSourceReader` | NDJSON 파일을 chunk 기반으로 읽는다. |
| `RoutingBackfillSourceReader` | source type에 따라 실제 reader를 고른다. |
| `StubBackfillSourceReader` | 파일이 없을 때 fallback 샘플 데이터를 제공한다. |

### `infrastructure.target`
| 클래스 | 역할 |
| --- | --- |
| `DeviceLastSeenBackfillWriter` | `device_last_seen` 재계산 결과를 기록한다. |
| `DrivingEventCounterBackfillWriter` | `driving_event_counter` 재계산 결과를 기록한다. |
| `DryRunBackfillTargetWriter` | 실제 반영 없이 dry-run 결과만 남긴다. |
| `EventsPerMinuteBackfillWriter` | `events_per_minute` 재계산 결과를 기록한다. |
| `RegionHeatmapBackfillWriter` | `region_heatmap` 재계산 결과를 기록한다. |
| `RoutingBackfillTargetWriter` | dry-run 여부와 target에 따라 writer를 선택한다. |

## 현재 구현 범위
- plan 준비
- chunk 기반 source read
- dry-run 및 실제 target write
- `DEVICE_LAST_SEEN`, `EVENTS_PER_MINUTE`, `DRIVING_EVENT_COUNTER`, `REGION_HEATMAP` 재계산

## 구조 변경 이유
job 설정, source 읽기, target 쓰기가 한 패키지에 섞여 있으면 backfill 흐름을 이해하기 어렵다. 지금은 실행 계획, 입출력 포트, 실제 source/target 구현을 분리해서 읽기와 확장이 쉬워졌다.
