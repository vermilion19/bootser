package com.booster.kotlin.boardservice.post.infrastructure

import com.booster.kotlin.boardservice.post.domain.PostSummary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

data class PostRow(
    val id: Long,
    val title: String,
    val author: String,
    val contentPreview: String,
)

@Repository
class PostJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbc: NamedParameterJdbcTemplate,
) {

    // RowMapper: ResultSet의 현재 행을 원하는 객체로 변환하는 함수
    // rs = ResultSet (쿼리 결과), _ = rowNum (사용 안 함)
    private val rowMapper = RowMapper<PostRow> { rs, _ ->
        PostRow(
            id = rs.getLong("id"),
            title = rs.getString("title"),
            author = rs.getString("author"),
            contentPreview = rs.getString("content").take(100),
        )
    }

    // -------------------------------------------------------------------------
    // 1. JdbcTemplate — ? 위치 기반 바인딩
    //    vararg 마지막 인자로 ? 순서대로 값을 전달
    //    단점: 파라미터가 많아지면 순서 실수 위험
    // -------------------------------------------------------------------------
    fun findTopN(limit: Int): List<PostRow> {
        val sql = "SELECT id, title, author, content FROM posts ORDER BY id DESC LIMIT ?"
        return jdbcTemplate.query(sql, rowMapper, limit)
    }

    // -------------------------------------------------------------------------
    // 2. NamedParameterJdbcTemplate — :name 이름 기반 바인딩
    //    MapSqlParameterSource 로 파라미터 맵 구성
    //    가독성이 좋고, 파라미터 순서 실수가 없음 → 파라미터 多 시 선호
    // -------------------------------------------------------------------------
    fun searchByKeyword(keyword: String): List<PostRow> {
        val sql = """
            SELECT id, title, author, content
            FROM posts
            WHERE title LIKE :pattern OR content LIKE :pattern
            ORDER BY id DESC
        """.trimIndent()

        val params = MapSqlParameterSource("pattern", "%$keyword%")
        return namedJdbc.query(sql, params, rowMapper)
    }

    // -------------------------------------------------------------------------
    // 3. queryForObject — 단일 값(Scalar) 반환
    //    결과가 0건이면 EmptyResultDataAccessException 발생
    //    결과가 2건 이상이면 IncorrectResultSizeDataAccessException 발생
    // -------------------------------------------------------------------------
    fun countAll(): Long {
        val sql = "SELECT COUNT(*) FROM posts"
        return jdbcTemplate.queryForObject(sql, Long::class.java) ?: 0L
    }

    // -------------------------------------------------------------------------
    // 4. namedJdbc.update — INSERT / UPDATE / DELETE
    //    반환값: 영향받은 행(row) 수
    // -------------------------------------------------------------------------
    fun bulkUpdateAuthor(oldAuthor: String, newAuthor: String): Int {
        val sql = "UPDATE posts SET author = :newAuthor WHERE author = :oldAuthor"
        val params = MapSqlParameterSource()
            .addValue("oldAuthor", oldAuthor)
            .addValue("newAuthor", newAuthor)
        return namedJdbc.update(sql, params)
    }

    // -------------------------------------------------------------------------
    // 5. batchUpdate — 여러 행을 한 번에 처리 (INSERT 다건 등)
    //    PreparedStatement 재사용으로 N번 INSERT보다 성능 우수
    //    반환값: 각 쿼리별 영향받은 행 수 배열
    // -------------------------------------------------------------------------
    fun batchInsert(posts: List<Triple<String, String, String>>): IntArray {
        val sql = "INSERT INTO posts (title, content, author) VALUES (?, ?, ?)"
        return jdbcTemplate.batchUpdate(sql, posts.map { (title, content, author) ->
            arrayOf(title, content, author)
        })
    }

    // -------------------------------------------------------------------------
    // 참고: BeanPropertyRowMapper — 컬럼명과 프로퍼티명이 일치할 때 자동 매핑
    //   val rows = jdbcTemplate.query(sql, BeanPropertyRowMapper(PostRow::class.java))
    //   단점: 리플렉션 기반이라 성능이 커스텀 RowMapper보다 느림
    //         data class와는 잘 맞지 않음 (기본 생성자 필요)
    // -------------------------------------------------------------------------
}
