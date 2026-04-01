package com.booster.queryburst.product.domain;

import com.booster.queryburst.product.application.dto.CategoryResult;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.booster.queryburst.product.domain.QCategory.*;

@Repository
@RequiredArgsConstructor
public class CategoryQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<CategoryResult> findByCursor(Long cursorId, int size) {

        BooleanBuilder condition = getCondition(cursorId);
        return queryFactory
                .select(Projections.constructor(CategoryResult.class,
                        category.id,
                        category.name,
                        category.parent.id,
                        category.depth
                ))
                .from(category)
                .where(condition)
                .orderBy(category.id.desc())
                .limit(size + 1L)
                .fetch();
    }



    private BooleanBuilder getCondition(Long cursorId) {
        if (cursorId != null) {
            return new BooleanBuilder(category.id.lt(cursorId));
        } else {
            return new BooleanBuilder();
        }
    }


}
