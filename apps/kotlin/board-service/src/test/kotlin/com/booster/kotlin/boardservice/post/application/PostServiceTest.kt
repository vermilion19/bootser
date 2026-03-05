package com.booster.kotlin.boardservice.post.application

import com.booster.kotlin.boardservice.post.domain.Post
import com.booster.kotlin.boardservice.post.domain.PostDeleteResult
import com.booster.kotlin.boardservice.post.domain.PostRepository
import com.booster.kotlin.boardservice.post.domain.PostResult
import com.booster.kotlin.boardservice.post.infrastructure.PostJdbcRepository
import com.booster.kotlin.boardservice.post.infrastructure.PostRow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

class PostServiceTest : DescribeSpec({

    val postRepository = mockk<PostRepository>()
    val postJdbcRepository = mockk<PostJdbcRepository>()
    val postService = PostService(postRepository, postJdbcRepository)

    afterEach { clearAllMocks() }

    // =========================================================================
    // create
    // =========================================================================
    describe("create") {
        it("Post를 저장하고 반환한다") {
            val post = Post.create("제목", "내용", "홍길동")
            every { postRepository.save(any()) } returns post

            val result = postService.create("제목", "내용", "홍길동")

            result shouldBe post
            verify(exactly = 1) { postRepository.save(any()) }
        }
    }

    // =========================================================================
    // getById — PostResult 반환
    // =========================================================================
    describe("getById") {
        context("존재하는 id를 요청하면") {
            it("PostResult.Success에 Post를 담아 반환한다") {
                val post = Post.create("제목", "내용", "홍길동")
                every { postRepository.findById(1L) } returns Optional.of(post)

                val result = postService.getById(1L)

                // shouldBeInstanceOf로 타입 검증 + smart cast
                result.shouldBeInstanceOf<PostResult.Success>()
                result.post shouldBe post
            }
        }

        context("존재하지 않는 id를 요청하면") {
            it("PostResult.NotFound에 id를 담아 반환한다") {
                every { postRepository.findById(999L) } returns Optional.empty()

                val result = postService.getById(999L)

                result.shouldBeInstanceOf<PostResult.NotFound>()
                result.id shouldBe 999L
            }
        }
    }

    // =========================================================================
    // update — PostResult 반환
    // =========================================================================
    describe("update") {
        context("존재하는 id를 요청하면") {
            it("title, content가 변경된 Post를 PostResult.Success에 담아 반환한다") {
                val post = Post.create("원래 제목", "원래 내용", "홍길동")
                every { postRepository.findById(1L) } returns Optional.of(post)

                val result = postService.update(1L, "새 제목", "새 내용")

                result.shouldBeInstanceOf<PostResult.Success>()
                result.post.title shouldBe "새 제목"
                result.post.content shouldBe "새 내용"
            }
        }

        context("존재하지 않는 id를 요청하면") {
            it("PostResult.NotFound에 id를 담아 반환한다") {
                every { postRepository.findById(999L) } returns Optional.empty()

                val result = postService.update(999L, "제목", "내용")

                result.shouldBeInstanceOf<PostResult.NotFound>()
                result.id shouldBe 999L
            }
        }
    }

    // =========================================================================
    // delete — PostDeleteResult 반환
    // =========================================================================
    describe("delete") {
        context("존재하는 id를 요청하면") {
            it("삭제 후 PostDeleteResult.Deleted를 반환한다") {
                every { postRepository.existsById(1L) } returns true
                every { postRepository.deleteById(1L) } returns Unit

                val result = postService.delete(1L)

                result.shouldBeInstanceOf<PostDeleteResult.Deleted>()
                verify(exactly = 1) { postRepository.deleteById(1L) }
            }
        }

        context("존재하지 않는 id를 요청하면") {
            it("PostDeleteResult.NotFound에 id를 담아 반환하고 deleteById는 호출하지 않는다") {
                every { postRepository.existsById(999L) } returns false

                val result = postService.delete(999L)

                result.shouldBeInstanceOf<PostDeleteResult.NotFound>()
                result.id shouldBe 999L
                verify(exactly = 0) { postRepository.deleteById(any()) }
            }
        }
    }

    // =========================================================================
    // findByAuthor
    // =========================================================================
    describe("findByAuthor") {
        it("author로 게시글 목록을 반환한다") {
            val posts = listOf(
                Post.create("제목1", "내용1", "홍길동"),
                Post.create("제목2", "내용2", "홍길동"),
            )
            every { postRepository.findAllByAuthor("홍길동") } returns posts

            val result = postService.findByAuthor("홍길동")

            result.size shouldBe 2
            verify(exactly = 1) { postRepository.findAllByAuthor("홍길동") }
        }
    }

    // =========================================================================
    // searchByKeyword
    // =========================================================================
    describe("searchByKeyword") {
        it("키워드로 게시글 페이지를 반환한다") {
            val posts = listOf(Post.create("스프링 입문", "내용", "홍길동"))
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(posts, pageable, 1)
            every { postRepository.searchByKeyword("스프링", pageable) } returns page

            val result = postService.searchByKeyword("스프링", pageable)

            result.totalElements shouldBe 1
        }
    }

    // =========================================================================
    // findRecentPosts (JdbcTemplate)
    // =========================================================================
    describe("findRecentPosts") {
        it("최신 N개의 게시글 요약을 반환한다") {
            val rows = listOf(
                PostRow(1L, "제목1", "홍길동", "내용 미리보기"),
                PostRow(2L, "제목2", "김철수", "내용 미리보기"),
            )
            every { postJdbcRepository.findTopN(2) } returns rows

            val result = postService.findRecentPosts(2)

            result.size shouldBe 2
            verify(exactly = 1) { postJdbcRepository.findTopN(2) }
        }
    }
})
