package com.booster.queryburst.member.web.dto.response;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> content,
        boolean hasNext,
        Long nextCursor  // 다음 요청 시 cursor 파라미터로 사용할 값
) {
    public static <T> CursorPageResponse<T> of(List<T> content, boolean hasNext, Long nextCursor) {
        return new CursorPageResponse<>(content, hasNext, nextCursor);
    }
}