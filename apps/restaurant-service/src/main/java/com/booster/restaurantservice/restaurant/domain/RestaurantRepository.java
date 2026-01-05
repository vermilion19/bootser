package com.booster.restaurantservice.restaurant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant,Long> {

    /**
     * 입장 처리 (Atomic Update)
     * 조건: 현재 인원이 수용 인원보다 작을 때만 +1 증가
     * 반환값: 업데이트된 행의 수 (1이면 성공, 0이면 실패-만석)
     */
    @Modifying(clearAutomatically = true) // 벌크 연산 후 영속성 컨텍스트 초기화
    @Query("""
        UPDATE Restaurant r 
        SET r.currentOccupancy = r.currentOccupancy + :partySize 
        WHERE r.id = :id 
          AND r.currentOccupancy < r.capacity
    """)
    int increaseOccupancy(@Param("id") Long id, @Param("partySize") int partySize);


    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Restaurant r 
        SET r.currentOccupancy = r.currentOccupancy - :partySize 
        WHERE r.id = :id 
          AND (r.currentOccupancy + :partySize) <= r.capacity 
    """)
    int decreaseOccupancy(@Param("id") Long id, @Param("partySize") int partySize);
}
