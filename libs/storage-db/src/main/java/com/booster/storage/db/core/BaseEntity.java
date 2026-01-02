package com.booster.storage.db.core;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 이 클래스는 테이블로 안 만들고, 상속받는 자식 테이블에게 컬럼만 줌
@EntityListeners(AuditingEntityListener.class) // JPA에게 "이거 시간 자동 감시해!"라고 알림
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false) // 생성 시간은 수정되면 안 됨
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}