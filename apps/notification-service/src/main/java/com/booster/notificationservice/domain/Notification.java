package com.booster.notificationservice.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "notification_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification{

    @Id
    private Long id;

    private Long restaurantId;
    private Long waitingId;

    // 누구한테 보냈는지 (슬랙은 채널방이라 애매하지만, 나중에 SMS/카톡 확장을 위해)
    private String target;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status; // PENDING, SENT, FAILED

    private LocalDateTime sentAt; // 실제 전송 완료 시간

    @CreationTimestamp
    private LocalDateTime createdAt; // 요청 받은 시간

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            // 시간순 정렬되는 64비트 Long ID 생성 (Snowflake와 동일 효과)
            this.id = SnowflakeGenerator.nextId();
        }
    }

    // 생성자 및 편의 메서드...
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
