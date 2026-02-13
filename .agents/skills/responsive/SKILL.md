---
name: responsive
description: 반응형 레이아웃 및 브레이크포인트 검토
---

## 작업

$ARGUMENTS로 지정된 컴포넌트의 반응형 레이아웃을 검토하고 다양한 화면 크기에서의 문제점을 분석합니다.

### 검토 대상

- `$ARGUMENTS`가 파일/컴포넌트명이면: 해당 컴포넌트 반응형 검토
- `$ARGUMENTS`가 디렉토리면: 해당 경로 내 전체 컴포넌트 검토
- `$ARGUMENTS`가 없으면: 현재 변경된 파일 대상 검토

### 브레이크포인트 기준 (Tailwind CSS)

| Prefix | 최소 너비 | 대상 디바이스 |
|--------|----------|--------------|
| (없음) | 0px | 모바일 (기본) |
| `sm:` | 640px | 소형 태블릿 |
| `md:` | 768px | 태블릿 |
| `lg:` | 1024px | 소형 데스크톱 |
| `xl:` | 1280px | 데스크톱 |
| `2xl:` | 1536px | 대형 모니터 |

### 검토 관점

#### 1. 레이아웃 구조
- Mobile-first 접근 여부 (기본 스타일 → 큰 화면 확장)
- Flexbox/Grid 방향 전환 (`flex-col` → `md:flex-row`)
- 컨테이너 너비 제한 (`max-w-*`, `container`)
- 오버플로우 처리 (`overflow-hidden`, `overflow-x-auto`)

#### 2. 타이포그래피
- 화면 크기별 폰트 사이즈 조절 (`text-sm md:text-base lg:text-lg`)
- 긴 텍스트 줄바꿈 처리 (`truncate`, `line-clamp-*`, `break-words`)
- 제목/본문 비율 유지

#### 3. 네비게이션
- 모바일 햄버거 메뉴 전환 로직
- 사이드바 접힘/펼침 (`hidden md:block`)
- 탭/드롭다운 전환 패턴
- 터치 영역 최소 크기 (44x44px)

#### 4. 데이터 표시
- 테이블 → 카드 뷰 전환 (모바일)
- 수평 스크롤 테이블 (`overflow-x-auto`)
- 그리드 컬럼 조정 (`grid-cols-1 md:grid-cols-2 lg:grid-cols-3`)
- 이미지 비율 유지 (`aspect-ratio`, `object-cover`)

#### 5. 간격 및 여백
- 패딩/마진 반응형 조절 (`p-4 md:p-6 lg:p-8`)
- 모바일에서 불필요한 여백 과다
- 요소 간 간격 일관성 (`gap-*`)

#### 6. 인터랙션
- 호버 → 터치 대응 (`hover:` 모바일 대체)
- 모달/팝오버 모바일 풀스크린 전환
- 스크롤 동작 (`sticky`, `fixed` 위치 적절성)
- 키보드 접근성 유지

### 출력 형식

```markdown
## 반응형 검토 결과

### 요약
- 분석 대상: [컴포넌트명]
- 심각도 분포: Critical N건 / Warning N건 / Info N건
- 지원 범위: Mobile / Tablet / Desktop

---

### Critical (레이아웃 깨짐)

#### [C-1] 모바일에서 가로 스크롤 발생
- **위치**: `Component.tsx:25`
- **문제**: `flex-row`가 모바일에서도 적용되어 컨텐츠가 넘침
- **영향**: 320px~640px 화면에서 가로 스크롤
- **해결**:
```tsx
// Before
<div className="flex flex-row gap-4">

// After
<div className="flex flex-col md:flex-row gap-4">
```

### Warning (개선 권장)

#### [W-1] 터치 영역 부족
- **위치**: `Component.tsx:42`
- **문제**: 버튼 패딩이 `p-1`로 터치 영역 부족
- **해결**: 최소 `p-2` 또는 `min-h-[44px] min-w-[44px]`

### Info (참고)

#### [I-1] 제목
- **내용**: 설명 및 제안

### 브레이크포인트별 미리보기

| 요소 | Mobile (< 640px) | Tablet (768px) | Desktop (1024px+) |
|------|------------------|----------------|-------------------|
| 네비게이션 | 햄버거 메뉴 | 축소 사이드바 | 전체 사이드바 |
| 그리드 | 1열 | 2열 | 3열 |
| 테이블 | 카드 뷰 | 스크롤 테이블 | 전체 테이블 |
```

### 사용 예시

```
/responsive NavigationBar.tsx               # 모바일 햄버거 메뉴 전환 로직 확인
/responsive LandingPage                     # 브레이크포인트별 레이아웃 적절성 검토
/responsive components/dashboard/           # 대시보드 컴포넌트 전체 검토
/responsive                                 # 현재 변경 파일 대상 검토
```
