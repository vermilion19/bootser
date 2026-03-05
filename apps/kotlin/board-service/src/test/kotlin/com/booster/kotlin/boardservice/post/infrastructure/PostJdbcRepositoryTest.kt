package com.booster.kotlin.boardservice.post.infrastructure

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class PostJdbcRepositoryTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var postJdbcRepository: PostJdbcRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    init {
        beforeEach {
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

        // =====================================================================
        // 1. JdbcTemplate - ? 위치 기반 바인딩 + RowMapper
        // =====================================================================
        describe("findTopN") {
            context("limit이 전체 건수보다 작으면") {
                it("limit만큼만 반환한다") {
                    postJdbcRepository.findTopN(3) shouldHaveSize 3
                }
            }

            context("limit이 전체 건수보다 크면") {
                it("전체 건수를 반환한다") {
                    postJdbcRepository.findTopN(100) shouldHaveSize 5
                }
            }

            it("id DESC 순서로 정렬된다") {
                val result = postJdbcRepository.findTopN(5)
                val ids = result.map { it.id }
                ids shouldBe ids.sortedDescending()
            }
        }

        // =====================================================================
        // 2. NamedParameterJdbcTemplate - :name 이름 기반 바인딩
        // =====================================================================
        describe("searchByKeyword") {
            context("title에 키워드가 있으면") {
                it("해당 게시글을 반환한다") {
                    val result = postJdbcRepository.searchByKeyword("스프링")

                    result shouldHaveSize 2
                    result.all {
                        it.title.contains("스프링") || it.contentPreview.contains("스프링")
                    } shouldBe true
                }
            }

            context("일치하는 게시글이 없으면") {
                it("빈 리스트를 반환한다") {
                    postJdbcRepository.searchByKeyword("전혀없는키워드xyz").shouldBeEmpty()
                }
            }

            context("content가 100자를 초과하면") {
                it("contentPreview는 최대 100자로 잘린다") {
                    jdbcTemplate.update(
                        "INSERT INTO posts (title, content, author) VALUES (?, ?, ?)",
                        "긴 content 게시글", "a".repeat(200), "테스터"
                    )

                    val result = postJdbcRepository.searchByKeyword("긴 content")

                    result[0].contentPreview.length shouldBe 100
                }
            }
        }

        // =====================================================================
        // 3. queryForObject - 단일 Scalar 반환
        // =====================================================================
        describe("countAll") {
            it("전체 게시글 수를 반환한다") {
                postJdbcRepository.countAll() shouldBeExactly 5L
            }
        }

        // =====================================================================
        // 4. namedJdbc.update - DML
        // =====================================================================
        describe("bulkUpdateAuthor") {
            context("대상 author가 존재할 때") {
                it("영향받은 행 수를 반환하고 author가 변경된다") {
                    val updatedCount = postJdbcRepository.bulkUpdateAuthor("홍길동", "홍길동_변경")

                    updatedCount shouldBe 2
                    val verifyCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM posts WHERE author = ?",
                        Long::class.java,
                        "홍길동_변경"
                    )
                    verifyCount shouldBe 2L
                }
            }

            context("대상 author가 없으면") {
                it("0을 반환한다") {
                    postJdbcRepository.bulkUpdateAuthor("없는사람", "누군가") shouldBe 0
                }
            }
        }

        // =====================================================================
        // 5. batchUpdate - 다건 INSERT
        // =====================================================================
        describe("batchInsert") {
            it("전달한 건수만큼 INSERT하고 전체 카운트가 증가한다") {
                val newPosts = listOf(
                    Triple("배치1", "내용1", "테스터"),
                    Triple("배치2", "내용2", "테스터"),
                    Triple("배치3", "내용3", "테스터"),
                )

                val results = postJdbcRepository.batchInsert(newPosts)

                results shouldHaveSize 3
                results.all { it == 1 } shouldBe true
                postJdbcRepository.countAll() shouldBeExactly 8L
            }
        }
    }
}
