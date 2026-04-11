package com.booster.queryburstmsa.member.domain.repository;

import com.booster.queryburstmsa.member.domain.entity.MemberEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    @Query("""
            select m
            from MemberEntity m
            where (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<MemberEntity> findMembers(@Param("cursor") Long cursor, Pageable pageable);
}
