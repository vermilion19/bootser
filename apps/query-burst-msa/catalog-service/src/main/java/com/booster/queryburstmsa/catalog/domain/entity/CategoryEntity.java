package com.booster.queryburstmsa.catalog.domain.entity;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "catalog_category",
        indexes = {
                @Index(name = "idx_catalog_category_parent_id", columnList = "parent_id"),
                @Index(name = "idx_catalog_category_depth", columnList = "depth")
        }
)
public class CategoryEntity extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private int depth;

    protected CategoryEntity() {
    }

    public static CategoryEntity create(String name, Long parentId, int depth) {
        CategoryEntity entity = new CategoryEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.name = name;
        entity.parentId = parentId;
        entity.depth = depth;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public int getDepth() {
        return depth;
    }
}
