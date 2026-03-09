package com.booster.kotlin.boardservice.comment.application

import com.booster.kotlin.boardservice.comment.application.dto.CreateCommentCommand
import com.booster.kotlin.boardservice.comment.application.dto.UpdateCommentCommand
import com.booster.kotlin.boardservice.comment.domain.Comment
import com.booster.kotlin.boardservice.comment.domain.CommentDeleteResult
import com.booster.kotlin.boardservice.comment.domain.CommentRepository
import com.booster.kotlin.boardservice.comment.domain.CommentResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class CommentServiceTest : DescribeSpec({

    val commentRepository = mockk<CommentRepository>()
    val commentService = CommentService(commentRepository)

    afterEach { clearAllMocks() }

    // =========================================================================
    // create
    // =========================================================================
    describe("create") {
        it("댓글을 저장하고 반환한다") {
            val command = CreateCommentCommand(postId = 1L, content = "댓글 내용", author = "홍길동")
            val comment = Comment.create(1L, "댓글 내용", "홍길동")
            every { commentRepository.save(any()) } returns comment

            val result = commentService.create(command)

            result shouldBe comment
            verify(exactly = 1) { commentRepository.save(any()) }
        }
    }

    // =========================================================================
    // findAllByPostId
    // =========================================================================
    describe("findAllByPostId") {
        it("postId에 해당하는 댓글 목록을 반환한다") {
            val comments = listOf(
                Comment.create(1L, "댓글1", "홍길동"),
                Comment.create(1L, "댓글2", "김철수"),
            )
            every { commentRepository.findAllByPostId(1L) } returns comments

            val result = commentService.findAllByPostId(1L)

            result.size shouldBe 2
            verify(exactly = 1) { commentRepository.findAllByPostId(1L) }
        }

        it("댓글이 없으면 빈 리스트를 반환한다") {
            every { commentRepository.findAllByPostId(999L) } returns emptyList()

            val result = commentService.findAllByPostId(999L)

            result.size shouldBe 0
        }
    }

    // =========================================================================
    // update
    // =========================================================================
    describe("update") {
        context("존재하는 댓글이고 작성자가 일치하면") {
            it("내용이 수정된 댓글을 CommentResult.Success에 담아 반환한다") {
                val comment = Comment.create(1L, "원래 내용", "홍길동")
                val command = UpdateCommentCommand(commentId = 1L, content = "수정된 내용", author = "홍길동")
                every { commentRepository.findById(1L) } returns Optional.of(comment)

                val result = commentService.update(command)

                result.shouldBeInstanceOf<CommentResult.Success>()
                result.comment.content shouldBe "수정된 내용"
            }
        }

        context("존재하지 않는 commentId를 요청하면") {
            it("CommentResult.NotFound에 id를 담아 반환한다") {
                val command = UpdateCommentCommand(commentId = 999L, content = "수정된 내용", author = "홍길동")
                every { commentRepository.findById(999L) } returns Optional.empty()

                val result = commentService.update(command)

                result.shouldBeInstanceOf<CommentResult.NotFound>()
                result.id shouldBe 999L
            }
        }

        context("작성자가 일치하지 않으면") {
            it("CommentResult.Forbidden에 id를 담아 반환한다") {
                val comment = Comment.create(1L, "원래 내용", "홍길동")
                val command = UpdateCommentCommand(commentId = 1L, content = "수정된 내용", author = "김철수")
                every { commentRepository.findById(1L) } returns Optional.of(comment)

                val result = commentService.update(command)

                result.shouldBeInstanceOf<CommentResult.Forbidden>()
                result.id shouldBe 1L
            }
        }
    }

    // =========================================================================
    // delete
    // =========================================================================
    describe("delete") {
        context("존재하는 댓글이고 작성자가 일치하면") {
            it("삭제 후 CommentDeleteResult.Deleted를 반환한다") {
                val comment = Comment.create(1L, "댓글 내용", "홍길동")
                every { commentRepository.findById(1L) } returns Optional.of(comment)
                every { commentRepository.delete(comment) } returns Unit

                val result = commentService.delete(1L, "홍길동")

                result.shouldBeInstanceOf<CommentDeleteResult.Deleted>()
                verify(exactly = 1) { commentRepository.delete(comment) }
            }
        }

        context("존재하지 않는 commentId를 요청하면") {
            it("CommentDeleteResult.NotFound에 id를 담아 반환하고 delete는 호출하지 않는다") {
                every { commentRepository.findById(999L) } returns Optional.empty()

                val result = commentService.delete(999L, "홍길동")

                result.shouldBeInstanceOf<CommentDeleteResult.NotFound>()
                result.id shouldBe 999L
                verify(exactly = 0) { commentRepository.delete(any()) }
            }
        }

        context("작성자가 일치하지 않으면") {
            it("CommentDeleteResult.Forbidden에 id를 담아 반환하고 delete는 호출하지 않는다") {
                val comment = Comment.create(1L, "댓글 내용", "홍길동")
                every { commentRepository.findById(1L) } returns Optional.of(comment)

                val result = commentService.delete(1L, "김철수")

                result.shouldBeInstanceOf<CommentDeleteResult.Forbidden>()
                result.id shouldBe 1L
                verify(exactly = 0) { commentRepository.delete(any()) }
            }
        }
    }
})
