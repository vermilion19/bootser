package com.booster.kotlin.boardservice.post.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostRepository : JpaRepository<Post, Long> {

    // -------------------------------------------------------------------------
    // 1. 기본 Native Query
    //    nativeQuery = true → JPQL 대신 실제 DB SQL 사용
    //    :author → @Param("author") 으로 바인딩
    // -------------------------------------------------------------------------
    @Query(
        value = "SELECT * FROM posts WHERE author = :author",
        nativeQuery = true
    )
    fun findAllByAuthor(@Param("author") author: String): List<Post>

    // -------------------------------------------------------------------------
    // 2. Native Query + Pageable
    //    Page 반환 시 countQuery 필수: Spring이 전체 건수 조회에 사용
    //    countQuery 없으면 원본 쿼리를 COUNT(*)로 감싸 오동작 가능성 있음
    // -------------------------------------------------------------------------
    @Query(
        value = """
            SELECT * FROM posts
            WHERE title LIKE CONCAT('%', :keyword, '%')
               OR content LIKE CONCAT('%', :keyword, '%')
        """,
        countQuery = """
            SELECT COUNT(*) FROM posts
            WHERE title LIKE CONCAT('%', :keyword, '%')
               OR content LIKE CONCAT('%', :keyword, '%')
        """,
        nativeQuery = true
    )
    fun searchByKeyword(@Param("keyword") keyword: String, pageable: Pageable): Page<Post>

    // -------------------------------------------------------------------------
    // 3. Interface Projection + Native Query
    //    SELECT 절 컬럼명이 PostSummary 인터페이스의 프로퍼티명과 일치해야 함
    //    필요한 컬럼만 조회 → 불필요한 content 로딩 방지
    // -------------------------------------------------------------------------
    @Query(
        value = "SELECT id, title, author FROM posts WHERE author = :author",
        nativeQuery = true
    )
    fun findSummaryByAuthor(@Param("author") author: String): List<PostSummary>

    // -------------------------------------------------------------------------
    // 4. 집계 쿼리 (Scalar 값 반환)
    //    Long 타입으로 직접 받을 수 있음
    // -------------------------------------------------------------------------
    @Query(
        value = "SELECT COUNT(*) FROM posts WHERE author = :author",
        nativeQuery = true
    )
    fun countByAuthor(@Param("author") author: String): Long

    // -------------------------------------------------------------------------
    // 5. @Modifying — DML(UPDATE/DELETE) native query
    //    @Modifying 없이 UPDATE/DELETE 실행 시 예외 발생
    //    clearAutomatically = true: 쿼리 실행 후 영속성 컨텍스트 1차 캐시 초기화
    //    → 벌크 연산 후 엔티티를 다시 조회해도 DB 값과 불일치 방지
    // -------------------------------------------------------------------------
    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE posts SET author = :newAuthor WHERE author = :oldAuthor",
        nativeQuery = true
    )
    fun bulkUpdateAuthor(
        @Param("oldAuthor") oldAuthor: String,
        @Param("newAuthor") newAuthor: String
    ): Int


    @Query("SELECT p FROM Post p JOIN FETCH p.postTags pt JOIN FETCH pt.tag where p.id = :id")
    fun findByIdWithTags(@Param("id") id: Long): Post?

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.postTags pt LEFT JOIN FETCH pt.tag")
    fun findAllWithTags(): List<Post>
}
