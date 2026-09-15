package com.booster.dday.release.web.dto;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.shared.web.DDayErrorCode;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 관심 등록 요청.
 *
 * <p>열거형을 문자열로 받고 <b>우리가 푼다.</b> Jackson 에게 맡기면 모르는 값에
 * 400 은 나가지만 «무엇을 쓸 수 있는지» 가 응답에 없다 — 기념일 쪽에서 같은 판단을
 * 했다 (C-7).
 */
public record WatchRequest(String subjectType, Long subjectId) {

    public SubjectType resolvedType() {
        SubjectType resolved = parse(subjectType);
        if (resolved == SubjectType.MOVIE_RELEASE) {
            /* ck_watch_subject 가 허용하지 않는다 — 관심은 작품에 걸고 개봉 회차에
               걸지 않는다. DB 제약이 막을 것을 먼저 막아 500 대신 400 이 나가게 한다 */
            throw new CoreException(DDayErrorCode.WATCH_SUBJECT_NOT_ALLOWED,
                    "개봉 회차에는 관심을 걸 수 없다. 작품에 걸어라");
        }
        return resolved;
    }

    private static SubjectType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "subjectType 이 없다. " + allowed());
        }
        try {
            return SubjectType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "모르는 subjectType: " + raw + ". " + allowed());
        }
    }

    private static String allowed() {
        return "쓸 수 있는 값은 " + Arrays.stream(SubjectType.values())
                .filter(type -> type != SubjectType.MOVIE_RELEASE)
                .map(Enum::name)
                .collect(Collectors.joining(" · "));
    }
}
