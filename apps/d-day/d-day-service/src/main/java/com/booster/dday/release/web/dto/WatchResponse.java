package com.booster.dday.release.web.dto;

import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Watch;

/** 내가 걸어 둔 관심 하나 (D-3) */
public record WatchResponse(
        Long id,
        SubjectType subjectType,
        Long subjectId
) {

    public static WatchResponse of(Watch watch) {
        return new WatchResponse(watch.getId(), watch.getSubjectType(), watch.getSubjectId());
    }
}
