package com.booster.dday.holiday.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HolidayNameLabelRepository
        extends JpaRepository<HolidayNameLabel, HolidayNameLabel.Key> {

    /** 언어 하나를 통째로. 캐시가 {@code lbl:{lang}} 한 키에 담는다 (ARCHITECTURE §4.2) */
    List<HolidayNameLabel> findAllByLang(String lang);
}
