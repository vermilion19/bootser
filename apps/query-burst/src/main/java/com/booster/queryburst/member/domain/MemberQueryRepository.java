package com.booster.queryburst.member.domain;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    // OFFSET 없는 커서 기반 조회 (v3)
    // cursorId가 null이면 첫 페이지, 아니면 해당 id 이전 데이터 조회
    public List<Member> findByCursor(Long cursorId, int size) {
        QMember member = QMember.member;

        BooleanBuilder condition = new BooleanBuilder();
        if (cursorId != null) {
            condition.and(member.id.lt(cursorId));
        }

        return queryFactory
                .selectFrom(member)
                .where(condition)
                .orderBy(member.id.desc())
                .limit(size + 1L) // hasNext 판단을 위해 1개 더 조회
                .fetch();
    }

}
