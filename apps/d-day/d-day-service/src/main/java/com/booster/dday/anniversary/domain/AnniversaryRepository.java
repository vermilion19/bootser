package com.booster.dday.anniversary.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {

    List<Anniversary> findAllByMemberIdOrderByIdDesc(Long memberId);

    /**
     * 남의 기념일을 못 보게 하는 자리.
     *
     * <p>{@code findById} 로 찾고 나서 주인을 확인하면 <b>그 확인을 한 군데서
     * 빠뜨리는 날 남의 기념일이 열린다.</b> 질의에 회원을 넣어 두면 빠뜨릴 자리가 없다.
     */
    Optional<Anniversary> findByIdAndMemberId(Long id, Long memberId);
}
