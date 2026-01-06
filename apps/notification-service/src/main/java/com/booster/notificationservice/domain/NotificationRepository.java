package com.booster.notificationservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {

    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 불일치 방지)
    @Query("UPDATE Notification n SET n.status = 'FAILED' WHERE n.waitingId IN :waitingIds")
    int updateStatusFailedByWaitingIds(@Param("waitingIds") List<Long> waitingIds);

    List<Notification> findAllByWaitingIdIn(List<Long> waitingIds);
}
