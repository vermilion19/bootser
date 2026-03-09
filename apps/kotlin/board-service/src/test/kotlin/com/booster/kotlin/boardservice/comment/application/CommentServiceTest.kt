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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

class CommentServiceTest : DescribeSpec({

    val commentRepository = mockk<CommentRepository>()
    val commentService = CommentService(commentRepository)

    afterEach { clearAllMocks() }

    // =========================================================================
    // create — 일반 댓글
    // =========================================================================
    describe("create") {
        context("parentId가 없으면") {
            it("일반 댓글을 저장하고 CommentResult.Success를 반환한다") {
                val command = CreateCommentCommand(postId = 1L, content = "댓글 내용", author = "홍길동")
                val comment = Comment.create(1L, "댓글 내용", "홍길동")
                every { commentRepository.save(any()) } returns comment

                val result = commentService.create(command)

                result.shouldBeInstanceOf<CommentResult.Success>()
                verify(exactly = 1) { commentRepository.save(any()) }
            }
        }

        context("parentId가 있고 부모 댓글이 존재하면") {
            it("대댓글을 저장하고 CommentResult.Success를 반환한다") {
                val parent = Comment.create(1L, "부모 댓글", "홍길동")
                val reply = Comment.createReply(1L, "대댓글 내용", "김철수", 1L)
                val command = CreateCommentCommand(postId = 1L, content = "대댓글 내용", author = "김철수", parentId = 1L)
                every { commentRepository.findById(1L) } returns Optional.of(parent)
                every { commentRepository.save(any()) } returns reply

                val result = commentService.create(command)

                result.shouldBeInstanceOf<CommentResult.Success>()
                result.comment.parentId shouldBe 1L
            }
        }

        context("parentId가 있지만 부모 댓글이 존재하지 않으면") {
            it("CommentResult.NotFound를 반환한다") {
                val command = CreateCommentCommand(postId = 1L, content = "대댓글", author = "김철수", parentId = 999L)
                every { commentRepository.findById(999L) } returns Optional.empty()

                val result = commentService.create(command)

                result.shouldBeInstanceOf<CommentResult.NotFound>()
                result.id shouldBe 999L
            }
        }

        context("parentId가 이미 대댓글인 댓글을 가리키면") {
            it("CommentResult.InvalidParent를 반환한다") {
                val reply = Comment.createReply(1L, "대댓글", "홍길동", 1L)
                val command = CreateCommentCommand(postId = 1L, content = "대대댓글", author = "김철수", parentId = 2L)
                every { commentRepository.findById(2L) } returns Optional.of(reply)

                val result = commentService.create(command)

                result.shouldBeInstanceOf<CommentResult.InvalidParent>()
                result.id shouldBe 2L
            }
        }
    }

    // =========================================================================
    // findAllByPostId — 페이징
    // =========================================================================
    describe("findAllByPostId") {
        it("최상위 댓글 목록을 페이지로 반환한다") {
            val pageable = PageRequest.of(0, 10)
            val comments = listOf(
                Comment.create(1L, "댓글1", "홍길동"),
                Comment.create(1L, "댓글2", "김철수"),
            )
            val page = PageImpl(comments, pageable, 2)
            every { commentRepository.findAllByPostIdAndParentIdIsNull(1L, pageable) } returns page

            val result = commentService.findAllByPostId(1L, pageable)

            result.totalElements shouldBe 2
            result.content.size shouldBe 2
            verify(exactly = 1) { commentRepository.findAllByPostIdAndParentIdIsNull(1L, pageable) }
        }

        it("댓글이 없으면 빈 페이지를 반환한다") {
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(emptyList<Comment>(), pageable, 0)
            every { commentRepository.findAllByPostIdAndParentIdIsNull(999L, pageable) } returns page

            val result = commentService.findAllByPostId(999L, pageable)

            result.totalElements shouldBe 0
        }
    }

    // =========================================================================
    // findReplies
    // =========================================================================
    describe("findReplies") {
        it("해당 댓글의 대댓글 목록을 반환한다") {
            val replies = listOf(
                Comment.createReply(1L, "대댓글1", "홍길동", 1L),
                Comment.createReply(1L, "대댓글2", "김철수", 1L),
            )
            every { commentRepository.findAllByParentId(1L) } returns replies

            val result = commentService.findReplies(1L)

            result.size shouldBe 2
            result.all { it.parentId == 1L } shouldBe true
        }

        it("대댓글이 없으면 빈 리스트를 반환한다") {
            every { commentRepository.findAllByParentId(999L) } returns emptyList()

            val result = commentService.findReplies(999L)

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
