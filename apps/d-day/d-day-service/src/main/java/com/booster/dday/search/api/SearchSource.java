package com.booster.dday.search.api;

import java.util.List;

/**
 * 검색에 자기 자료를 내놓는 컨텍스트 (SPEC §9.8 · §E-2).
 *
 * <h2>왜 색인 테이블이 아니라 포트인가</h2>
 *
 * <p>§9.8 의 초안은 {@code SearchDocument} 라는 <b>별도 색인</b>을 그려 두고 «색인을
 * 세울지 각 테이블을 직접 질의할지는 아직 결정이 아니다» 라고 남겨 두었다.
 * <b>직접 질의로 닫는다.</b> 근거 셋.
 *
 * <ol>
 *   <li><b>색인은 원본과 갈라진다.</b> 갱신 경로가 하나 더 생기고, 갈라진 것은
 *       조용하다 — 검색에만 안 나오고 자원 주소로는 멀쩡히 나온다. 그것을 알아챌
 *       방법이 없다</li>
 *   <li><b>규모가 작다.</b> 나라 204 · 언어별 공휴일 이름 라벨 수백 · 리그와 팀 수십 ·
 *       기념일은 사람당 몇 개다. 색인이 사 주는 것이 아직 없다</li>
 *   <li><b>{@code pg_trgm} GIN 을 지금 검증할 수 없다.</b> PostgreSQL 이 없다.
 *       확인 못 하는 인덱스를 설계에 박으면 그것은 설계가 아니라 짐작이다.
 *       SCHEMA §6.6 이 이미 «색인이 필요해지는 날 더하기만 하면 된다» 로 자리를
 *       비워 두었다</li>
 * </ol>
 *
 * <h2>{@code SyncTask} 와 같은 모양이다</h2>
 *
 * <p>{@code SyncOrchestrator} 가 {@code List<SyncTask>} 를 받듯 검색도
 * {@code List<SearchSource>} 를 받는다. <b>검색에 들어오는 컨텍스트가 느는 일이
 * 클래스 하나 더하는 일</b>이 되고, 서비스는 안 바뀐다 — 영화가 들어올 날 고칠 곳이
 * 한 군데다.
 *
 * <h2>{@link #scope()} 가 분류가 아니라 차단이다</h2>
 *
 * <p>{@link SearchScope#PERSONAL} 소스는 로그인 안 한 요청에서 <b>불리지도 않는다.</b>
 * 「빈 결과를 돌려준다」로 두면 그 판단이 소스마다 하나씩 생기고, 한 군데서
 * 빠뜨리는 날 남의 것이 열린다.
 */
public interface SearchSource {

    SearchKind kind();

    SearchScope scope();

    /**
     * 찾는다.
     *
     * <p><b>던지지 않는다.</b> 한 갈래가 터졌다고 검색 전체가 죽으면, 나머지 셋이
     * 멀쩡한데도 아무것도 안 나온다. 터질 만한 일은 부르는 쪽이 받아 적는다.
     *
     * @param memberId {@link SearchScope#PERSONAL} 일 때만 채워진다. 공개 소스는
     *                 이 값을 <b>받지도 않는다</b> — 받으면 언젠가 공개 자료를
     *                 사람별로 거르는 코드가 생긴다
     * @param limit    이 소스가 돌려줄 상한. <b>한 갈래가 응답을 독식하지 못하게</b> 한다
     */
    List<SearchHit> search(SearchTerm term, Long memberId, int limit);
}
