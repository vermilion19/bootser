package com.booster.kotlin.boardservice.post.domain

/**
 * JPA Interface-based Projection
 *
 * native query 결과를 엔티티 전체가 아닌 특정 컬럼만 매핑할 때 사용.
 * - SELECT 절의 컬럼 alias가 getter 이름과 일치해야 함 (id -> getId(), title -> getTitle())
 * - JPA가 런타임에 프록시 구현체를 자동 생성함
 * - SELECT * 대신 필요한 컬럼만 가져오므로 네트워크/메모리 효율적
 */
interface PostSummary {
    val id: Long
    val title: String
    val author: String
}