package com.booster.waitingservice.waiting.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting,Long> {
    // 특정 식당의 특정 손님이 대기 중인지 확인 (중복 등록 방지용)
    boolean existsByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);

    // 손님의 진행 중인 대기 정보 조회
    Optional<Waiting> findByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);

    // 오늘의 마지막 대기 번호 조회 (동시성 처리는 Facade의 락이 담당)
    // "오늘 생성된(createdAt >= startOfDay) 데이터 중 가장 큰 waitingNumber 조회"
    @Query("SELECT MAX(w.waitingNumber) FROM Waiting w " +
            "WHERE w.restaurantId = :restaurantId " +
            "AND w.createdAt >= :startOfDay")
    Integer findMaxWaitingNumber(@Param("restaurantId") Long restaurantId,
                                 @Param("startOfDay") LocalDateTime startOfDay);

    // 3. 내 앞의 대기 팀 수 계산 (나보다 번호가 작고, 상태가 WAITING인 사람 수)
    @Query("SELECT COUNT(w) FROM Waiting w " +
            "WHERE w.restaurantId = :restaurantId " +
            "AND w.status = 'WAITING' " +
            "AND w.waitingNumber < :myWaitingNumber")
    Long countAhead(@Param("restaurantId") Long restaurantId,
                    @Param("myWaitingNumber") int myWaitingNumber);

    /**
     * [스케줄러용] 현재 WAITING 상태인 모든 대기를 CANCELED로 변경
     * clearAutomatically = true : 영속성 컨텍스트를 비워줘서 데이터 불일치 방지
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Waiting w SET w.status = 'CANCELED' WHERE w.status = 'WAITING'")
    int bulkUpdateStatusToCanceled();

    @Modifying(clearAutomatically = true) // 👈 벌크 연산 후 영속성 컨텍스트 초기화
    @Query("""
        UPDATE Waiting w
        SET w.status = 'CANCELED'
        WHERE w.status = 'CALLED'
          AND w.updatedAt < :limitTime
    """)
    int updateStatusToNoShow(@Param("limitTime") LocalDateTime limitTime);

    /**
     * 커서 기반 페이지네이션으로 특정 식당의 대기 목록 조회
     * - 첫 페이지: cursor가 null이면 처음부터 조회
     * - 다음 페이지: cursor(waitingNumber) 이후 데이터만 조회
     * - waitingNumber 기준 오름차순 정렬 (대기 순서대로)
     */
    @Query("""
        SELECT w FROM Waiting w
        WHERE w.restaurantId = :restaurantId
          AND w.status = :status
          AND (:cursor IS NULL OR w.waitingNumber > :cursor)
        ORDER BY w.waitingNumber ASC
        LIMIT :size
    """)
    List<Waiting> findByRestaurantIdAndStatusWithCursor(
            @Param("restaurantId") Long restaurantId,
            @Param("status") WaitingStatus status,
            @Param("cursor") Integer cursor,
            @Param("size") int size
    );

    /**
     * 특정 식당의 특정 상태 대기 총 개수 조회
     */
    long countByRestaurantIdAndStatus(Long restaurantId, WaitingStatus status);
}
