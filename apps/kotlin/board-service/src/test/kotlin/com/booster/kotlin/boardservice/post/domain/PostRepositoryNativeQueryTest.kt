package com.booster.kotlin.boardservice.post.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
@DisplayName("PostRepository Native Query 테스트")
class PostRepositoryNativeQueryTest {

    @Autowired
    lateinit var postRepository: PostRepository

    @BeforeEach
    fun setUp() {
        postRepository.deleteAll()
        postRepository.saveAll(
            listOf(
                Post.create("스프링 입문", "스프링 기초 내용", "홍길동"),
                Post.create("스프링 심화", "스프링 고급 내용입니다", "홍길동"),
                Post.create("Kotlin 기초", "코틀린 기본 문법", "김철수"),
                Post.create("JPA 완전정복", "JPA native query 활용법", "김철수"),
                Post.create("Kotlin 코루틴", "비동기 프로그래밍 입문", "이영희"),
            )
        )
    }

    @Test
    @DisplayName("1. Native Query - author로 게시글 목록 조회")
    fun findAllByAuthor() {
        val posts = postRepository.findAllByAuthor("홍길동")

        assertThat(posts).hasSize(2)
        assertThat(posts).allMatch { it.author == "홍길동" }
    }

    @Test
    @DisplayName("1-1. Native Query - 존재하지 않는 author 조회 시 빈 리스트 반환")
    fun findAllByAuthor_notFound() {
        val posts = postRepository.findAllByAuthor("없는사람")

        assertThat(posts).isEmpty()
    }

    @Test
    @DisplayName("2. Native Query + Pageable - 키워드 검색 (title, content)")
    fun searchByKeyword() {
        val page = postRepository.searchByKeyword("스프링", PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).allMatch {
            it.title.contains("스프링") || it.content.contains("스프링")
        }
    }

    @Test
    @DisplayName("2-1. Native Query + Pageable - content에만 키워드가 있는 경우도 조회")
    fun searchByKeyword_inContent() {
        val page = postRepository.searchByKeyword("native query", PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].title).isEqualTo("JPA 완전정복")
    }

    @Test
    @DisplayName("2-2. Native Query + Pageable - 페이징이 올바르게 동작")
    fun searchByKeyword_paging() {
        val page = postRepository.searchByKeyword("", PageRequest.of(0, 2))

        assertThat(page.totalElements).isEqualTo(5)
        assertThat(page.content).hasSize(2)
        assertThat(page.totalPages).isEqualTo(3)
    }

    @Test
    @DisplayName("3. Interface Projection - author로 요약 조회 (id, title, author만)")
    fun findSummaryByAuthor() {
        val summaries = postRepository.findSummaryByAuthor("김철수")

        assertThat(summaries).hasSize(2)
        assertThat(summaries).allMatch { it.author == "김철수" }
        assertThat(summaries[0].title).isNotBlank()
    }

    @Test
    @DisplayName("4. 집계 쿼리 - author별 게시글 수 반환")
    fun countByAuthor() {
        val count = postRepository.countByAuthor("홍길동")

        assertThat(count).isEqualTo(2)
    }

    @Test
    @DisplayName("4-1. 집계 쿼리 - 게시글이 없는 author는 0 반환")
    fun countByAuthor_zero() {
        val count = postRepository.countByAuthor("없는사람")

        assertThat(count).isEqualTo(0)
    }

    @Test
    @DisplayName("5. @Modifying - 벌크 UPDATE 후 영향받은 행 수 반환")
    fun bulkUpdateAuthor() {
        val updatedCount = postRepository.bulkUpdateAuthor("홍길동", "홍길동_변경")

        assertThat(updatedCount).isEqualTo(2)
        val updated = postRepository.findAllByAuthor("홍길동_변경")
        assertThat(updated).hasSize(2)
    }

    @Test
    @DisplayName("5-1. @Modifying - 대상 없으면 0 반환")
    fun bulkUpdateAuthor_noTarget() {
        val updatedCount = postRepository.bulkUpdateAuthor("없는사람", "누군가")

        assertThat(updatedCount).isEqualTo(0)
    }
}
