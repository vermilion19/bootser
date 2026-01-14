package com.booster.waitingservice.waiting.web.dto.response;

import java.util.List;

/**
 * 커서 기반 페이지네이션 응답 래퍼
 * 무한 스크롤 방식에 최적화된 응답 구조
 *
 * @param content 조회된 데이터 목록
 * @param nextCursor 다음 페이지 조회용 커서 (마지막 페이지면 null)
 * @param hasNext 다음 페이지 존재 여부
 * @param totalCount 전체 데이터 개수 (선택적, 필요시 사용)
 * @param size 요청한 페이지 크기
 */
public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        Long totalCount,
        int size
) {
    /**
     * 커서 페이지 응답 생성
     *
     * @param content 조회된 데이터 목록
     * @param nextCursor 다음 페이지 커서
     * @param hasNext 다음 페이지 존재 여부
     * @param totalCount 전체 개수
     * @param size 페이지 크기
     */
    public static <T> CursorPageResponse<T> of(
            List<T> content,
            String nextCursor,
            boolean hasNext,
            Long totalCount,
            int size
    ) {
        return new CursorPageResponse<>(content, nextCursor, hasNext, totalCount, size);
    }

    /**
     * 전체 개수 없이 커서 페이지 응답 생성
     */
    public static <T> CursorPageResponse<T> of(
            List<T> content,
            String nextCursor,
            boolean hasNext,
            int size
    ) {
        return new CursorPageResponse<>(content, nextCursor, hasNext, null, size);
    }
}
