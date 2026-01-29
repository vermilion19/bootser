---
name: ui-gen
description: 설명을 바탕으로 현대적인 컴포넌트 코드 생성
user-invocable: true
---

## 작업

$ARGUMENTS의 설명을 바탕으로 Shadcn UI + Tailwind CSS 기반의 현대적인 UI 컴포넌트를 생성합니다.

### 입력

- `$ARGUMENTS`로 전달된 컴포넌트 설명
- 백엔드 API Response DTO 구조 (있는 경우)
- 참조할 디자인 요구사항

### 기술 스택

- **UI Library**: Shadcn UI (Radix UI 기반)
- **Styling**: Tailwind CSS
- **Framework**: React + TypeScript
- **State**: React hooks (useState, useEffect, useMemo)
- **Data Fetching**: fetch / axios (프로젝트에 맞게)
- **Icons**: Lucide React

### 생성 가능한 컴포넌트

| 유형 | 설명 |
|------|------|
| DataTable | 정렬, 필터링, 페이지네이션 지원 테이블 |
| Modal / Dialog | 확인, 폼 입력, 경고 등 다양한 모달 |
| Form | 유효성 검증 포함 입력 폼 |
| Dashboard | 차트, 통계 카드 조합 대시보드 |
| Card / List | 데이터 카드 또는 리스트 뷰 |
| Chart | 라인, 바, 파이 등 데이터 시각화 |
| Navigation | 사이드바, 탭, 브레드크럼 |
| Status / Badge | 상태 표시, 뱃지, 프로그레스 |

### 생성 규칙

#### 코드 품질
- TypeScript 타입 정의 필수 (interface/type)
- Props 인터페이스 명확하게 정의
- 재사용 가능한 컴포넌트 구조
- 접근성(a11y) 기본 지원 (aria-label, keyboard navigation)

#### 스타일링
- Tailwind CSS 유틸리티 클래스 사용
- 다크모드 지원 (`dark:` prefix)
- 반응형 디자인 (`sm:`, `md:`, `lg:`)
- Shadcn UI 컴포넌트 활용 (Button, Table, Dialog 등)

#### 데이터 연동
- 백엔드 Response DTO 구조에 맞는 타입 정의
- 로딩 상태 (Skeleton / Spinner)
- 에러 상태 처리
- 빈 데이터 상태 (Empty State)

### 출력 형식

```markdown
## 컴포넌트 생성 결과

### 개요
- 컴포넌트명: [ComponentName]
- 용도: 간단한 설명
- 의존성: Shadcn UI 컴포넌트 목록

### 필요한 Shadcn UI 설치

```bash
npx shadcn@latest add table button dropdown-menu
```

### 타입 정의

```typescript
interface User {
  id: number;
  name: string;
  email: string;
  status: "ACTIVE" | "INACTIVE";
}
```

### 컴포넌트 코드

```tsx
// components/user-data-table.tsx
"use client";

import { ... } from "@/components/ui/table";

interface UserDataTableProps {
  data: User[];
  onSort?: (column: string, direction: "asc" | "desc") => void;
}

export function UserDataTable({ data, onSort }: UserDataTableProps) {
  // 구현
}
```

### 사용 예시

```tsx
<UserDataTable
  data={users}
  onSort={handleSort}
/>
```
```

### 사용 예시

```
/ui-gen 사용자 목록을 보여주는 정렬 가능한 데이터 테이블
/ui-gen 실시간 트래픽을 보여주는 대시보드 차트 컴포넌트
/ui-gen 웨이팅 등록 폼 (인원수, 연락처 입력)
/ui-gen 식당 상세 정보 카드 컴포넌트
/ui-gen 알림 목록을 보여주는 드롭다운
```
