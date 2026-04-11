package com.booster.queryburstmsa.member.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.member.domain.MemberGrade;
import com.booster.queryburstmsa.member.web.dto.MemberCreateRequest;
import com.booster.queryburstmsa.member.web.dto.MemberUpdateRequest;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "member_master",
        indexes = {
                @Index(name = "idx_member_master_grade", columnList = "grade"),
                @Index(name = "idx_member_master_region", columnList = "region"),
                @Index(name = "idx_member_master_created_at", columnList = "created_at")
        }
)
public class MemberEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberGrade grade;

    @Column(nullable = false, length = 30)
    private String region;

    protected MemberEntity() {
    }

    public static MemberEntity create(MemberCreateRequest request) {
        MemberEntity entity = new MemberEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.email = request.email();
        entity.name = request.name();
        entity.grade = request.grade();
        entity.region = request.region();
        return entity;
    }

    public void update(MemberUpdateRequest request) {
        if (request.name() != null) {
            this.name = request.name();
        }
        if (request.grade() != null) {
            this.grade = request.grade();
        }
        if (request.region() != null) {
            this.region = request.region();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public MemberGrade getGrade() {
        return grade;
    }

    public String getRegion() {
        return region;
    }
}
