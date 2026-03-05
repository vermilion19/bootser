package com.booster.kotlin.boardservice.post.domain

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeZero
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class PostRepositoryNativeQueryTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var postRepository: PostRepository

    init {
        beforeEach {
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

        // =====================================================================
        // 1. 기본 Native Query
        // =====================================================================
        describe("findAllByAuthor") {
            context("author가 존재할 때") {
                it("해당 author의 게시글 목록을 반환한다") {
                    val result = postRepository.findAllByAuthor("홍길동")

                    result shouldHaveSize 2
                    result.all { it.author == "홍길동" } shouldBe true
                }
            }

            context("존재하지 않는 author를 요청하면") {
                it("빈 리스트를 반환한다") {
                    postRepository.findAllByAuthor("없는사람").shouldBeEmpty()
                }
            }
        }

        // =====================================================================
        // 2. Native Query + Pageable + countQuery
        // =====================================================================
        describe("searchByKeyword") {
            context("title에 키워드가 있으면") {
                it("해당 게시글을 반환한다") {
                    val result = postRepository.searchByKeyword("스프링", PageRequest.of(0, 10))

                    result.totalElements shouldBe 2
                    result.content.all {
                        it.title.contains("스프링") || it.content.contains("스프링")
                    } shouldBe true
                }
            }

            context("content에만 키워드가 있으면") {
                it("해당 게시글도 반환한다") {
                    val result = postRepository.searchByKeyword("native query", PageRequest.of(0, 10))

                    result.totalElements shouldBe 1
                    result.content[0].title shouldBe "JPA 완전정복"
                }
            }

            context("페이징을 요청하면") {
                it("size만큼만 content를 반환하고 totalElements는 전체 건수를 반환한다") {
                    val result = postRepository.searchByKeyword("", PageRequest.of(0, 2))

                    result.totalElements shouldBe 5
                    result.content shouldHaveSize 2
                    result.totalPages shouldBe 3
                }
            }
        }

        // =====================================================================
        // 3. Interface Projection
        // =====================================================================
        describe("findSummaryByAuthor") {
            context("author가 존재할 때") {
                it("id, title, author만 담긴 요약 목록을 반환한다") {
                    val result = postRepository.findSummaryByAuthor("김철수")

                    result shouldHaveSize 2
                    result.all { it.author == "김철수" } shouldBe true
                    result[0].title.shouldNotBeBlank()
                }
            }
        }

        // =====================================================================
        // 4. 집계 쿼리
        // =====================================================================
        describe("countByAuthor") {
            context("author가 존재할 때") {
                it("해당 author의 게시글 수를 반환한다") {
                    postRepository.countByAuthor("홍길동") shouldBe 2L
                }
            }

            context("존재하지 않는 author를 요청하면") {
                it("0을 반환한다") {
                    postRepository.countByAuthor("없는사람").shouldBeZero()
                }
            }
        }

        // =====================================================================
        // 5. @Modifying - 벌크 UPDATE
        // =====================================================================
        describe("bulkUpdateAuthor") {
            context("대상 author가 존재할 때") {
                it("영향받은 행 수를 반환하고 author가 변경된다") {
                    val updatedCount = postRepository.bulkUpdateAuthor("홍길동", "홍길동_변경")

                    updatedCount shouldBe 2
                    postRepository.findAllByAuthor("홍길동_변경") shouldHaveSize 2
                }
            }

            context("대상 author가 존재하지 않으면") {
                it("0을 반환한다") {
                    postRepository.bulkUpdateAuthor("없는사람", "누군가") shouldBe 0
                }
            }
        }
    }
}
