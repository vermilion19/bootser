package com.booster.dday.country.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 국가 저장소. <b>쓰기는 시드 하나뿐</b>이고 나머지는 전부 읽기다 (SPEC §9.2).
 *
 * <p>읽기를 여기서 직접 하지 않는다 — 다른 컨텍스트는 {@code country.api} 를 거친다
 * (ARCHITECTURE §1.5 R2).
 */
public interface CountryRepository extends JpaRepository<Country, String> {
}
