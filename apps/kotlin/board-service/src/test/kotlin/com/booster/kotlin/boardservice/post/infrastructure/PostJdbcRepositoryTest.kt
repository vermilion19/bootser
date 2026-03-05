package com.booster.kotlin.boardservice.post.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
@DisplayName("PostJdbcRepository JdbcTemplate 테스트")
class PostJdbcRepositoryTest {

    @Autowired
    lateinit var postJdbcRepository: PostJdbcRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM posts")
        jdbcTemplate.batchUpdate(
            "INSERT INTO posts (title, content, author) VALUES (?, ?, ?)",
            listOf(
                arrayOf("스프링 입문", "스프링 기초 내용", "홍길동"),
                arrayOf("스프링 심화", "스프링 고급 내용입니다", "홍길동"),
                arrayOf("Kotlin 기초", "코틀린 기본 문법", "김철수"),
                arrayOf("JPA 완전정복", "JPA native query 활용법", "김철수"),
                arrayOf("Kotlin 코루틴", "비동기 프로그래밍 입문", "이영희"),
            )
        )
    }

    @Test
    @DisplayName("1. findTopN - 최신 N건 조회")
    fun findTopN() {
        val result = postJdbcRepository.findTopN(3)

        assertThat(result).hasSize(3)
    }

    @Test
    @DisplayName("1-1. findTopN - limit이 전체 건수보다 크면 전체 반환")
    fun findTopN_limitExceedsTotal() {
        val result = postJdbcRepository.findTopN(100)

        assertThat(result).hasSize(5)
    }

    @Test
    @DisplayName("1-2. findTopN - id DESC 순서로 정렬")
    fun findTopN_orderedByIdDesc() {
        val result = postJdbcRepository.findTopN(5)

        val ids = result.map { it.id }
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    @DisplayName("2. searchByKeyword - title 또는 content에 키워드 포함")
    fun searchByKeyword() {
        val result = postJdbcRepository.searchByKeyword("스프링")

        assertThat(result).hasSize(2)
        assertThat(result).allMatch {
            it.title.contains("스프링") || it.contentPreview.contains("스프링")
        }
    }

    @Test
    @DisplayName("2-1. searchByKeyword - 일치하는 게시글 없으면 빈 리스트")
    fun searchByKeyword_notFound() {
        val result = postJdbcRepository.searchByKeyword("전혀없는키워드xyz")

        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("2-2. searchByKeyword - contentPreview는 100자 이하")
    fun searchByKeyword_contentPreviewLength() {
        // content가 100자 넘는 게시글 삽입
        val longContent = "a".repeat(200)
        jdbcTemplate.update(
            "INSERT INTO posts (title, content, author) VALUES (?, ?, ?)",
            "긴 content 게시글", longContent, "테스터"
        )

        val result = postJdbcRepository.searchByKeyword("긴 content")

        assertThat(result[0].contentPreview.length).isLessThanOrEqualTo(100)
    }

    @Test
    @DisplayName("3. countAll - 전체 게시글 수 반환")
    fun countAll() {
        val count = postJdbcRepository.countAll()

        assertThat(count).isEqualTo(5)
    }

    @Test
    @DisplayName("4. bulkUpdateAuthor - 영향받은 행 수 반환 및 실제 업데이트 확인")
    fun bulkUpdateAuthor() {
        val updatedCount = postJdbcRepository.bulkUpdateAuthor("홍길동", "홍길동_변경")

        assertThat(updatedCount).isEqualTo(2)

        val verifyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts WHERE author = ?",
            Long::class.java,
            "홍길동_변경"
        )
        assertThat(verifyCount).isEqualTo(2)
    }

    @Test
    @DisplayName("4-1. bulkUpdateAuthor - 대상 없으면 0 반환")
    fun bulkUpdateAuthor_noTarget() {
        val updatedCount = postJdbcRepository.bulkUpdateAuthor("없는사람", "누군가")

        assertThat(updatedCount).isEqualTo(0)
    }

    @Test
    @DisplayName("5. batchInsert - 다건 INSERT 후 건수 증가 확인")
    fun batchInsert() {
        val newPosts = listOf(
            Triple("배치1", "내용1", "테스터"),
            Triple("배치2", "내용2", "테스터"),
            Triple("배치3", "내용3", "테스터"),
        )

        val results = postJdbcRepository.batchInsert(newPosts)

        assertThat(results).hasSize(3)
        assertThat(results.all { it == 1 }).isTrue()
        assertThat(postJdbcRepository.countAll()).isEqualTo(8)
    }
}
