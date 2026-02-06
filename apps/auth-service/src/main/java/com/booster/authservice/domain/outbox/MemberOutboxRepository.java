package com.booster.authservice.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberOutboxRepository extends JpaRepository<MemberOutboxEvent,Long> {

    List<MemberOutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
}
