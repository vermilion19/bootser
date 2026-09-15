package com.booster.dday.release.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DateChangeRepository extends JpaRepository<DateChange, Long> {

    List<DateChange> findAllBySubjectTypeAndSubjectIdOrderByDetectedAtDesc(
            SubjectType subjectType, Long subjectId);

    /** 회차 보고에 쓴다 — <b>이번에 몇 건이 옮겨졌나</b> */
    long countByDetectedAtGreaterThanEqual(Instant from);
}
