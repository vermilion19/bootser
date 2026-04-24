# TelemetryHub Analytics API Review

## 목적
`analytics-api`는 `stream-processor`가 만든 read model 테이블을 조회 전용 API로 노출하는 서비스다.

## 현재 패키지 구조
```text
analyticsapi
├─ application
│  └─ query
└─ web
   └─ dto
```

## 패키지별 역할
| 패키지 | 역할 |
| --- | --- |
| `application.query` | query 실행과 view 모델 매핑 |
| `web` | 조회 API 진입점 |
| `web.dto` | API 응답 DTO |

## 패키지별 클래스 표

### `application.query`
| 클래스 | 역할 |
| --- | --- |
| `AnalyticsQueryService` | read model 테이블을 조회하고 view 모델로 매핑한다. |
| `DrivingEventCounterView` | driving event counter query 결과 view 모델이다. |
| `EventsPerMinuteView` | events per minute query 결과 view 모델이다. |
| `LatestDeviceView` | 디바이스 최신 상태 query 결과 view 모델이다. |
| `RegionHeatmapView` | 지역 heatmap query 결과 view 모델이다. |

### `web`
| 클래스 | 역할 |
| --- | --- |
| `AnalyticsController` | analytics 조회 REST API를 제공한다. |

### `web.dto`
| 클래스 | 역할 |
| --- | --- |
| `DrivingEventCounterResponse` | driving event counter 응답 DTO |
| `EventsPerMinuteResponse` | events per minute 응답 DTO |
| `LatestDeviceResponse` | latest device 응답 DTO |
| `RegionHeatmapResponse` | region heatmap 응답 DTO |

## 주요 API
- `GET /analytics/v1/devices/latest`
- `GET /analytics/v1/devices/{deviceId}/latest`
- `GET /analytics/v1/events-per-minute`
- `GET /analytics/v1/driving-events/counters`
- `GET /analytics/v1/heatmap/regions`

## 구조 변경 이유
규모가 작은 서비스라도 query 로직과 web 계층을 분리해두면 조회 모델이 늘어날 때 구조가 무너지지 않는다. 현재는 `application.query`에 view와 query를 모아서 조회 전용 서비스라는 점이 패키지 구조에서 바로 드러난다.
