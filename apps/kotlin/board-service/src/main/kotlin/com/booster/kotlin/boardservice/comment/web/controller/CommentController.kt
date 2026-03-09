package com.booster.kotlin.boardservice.comment.web.controller

import com.booster.kotlin.boardservice.comment.application.CommentService
import com.booster.kotlin.boardservice.comment.web.dto.request.CreateCommentRequest
import com.booster.kotlin.boardservice.comment.web.dto.request.UpdateCommentRequest
import com.booster.kotlin.boardservice.comment.web.dto.response.CommentResponse
import com.booster.kotlin.boardservice.comment.web.toResponseEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/posts/{postId}/comments")
class CommentController(
    private val commentService: CommentService,
) {

    @PostMapping
    fun create(
        @PathVariable postId: Long,
        @RequestBody request: CreateCommentRequest,
    ): ResponseEntity<CommentResponse> =
        commentService.create(request.toCommand()).toResponseEntity()

    @GetMapping
    fun getAll(
        @PathVariable postId: Long,
        @PageableDefault(size = 10) pageable: Pageable,
    ): ResponseEntity<Page<CommentResponse>> {
        val page = commentService.findAllByPostId(postId, pageable)
        return ResponseEntity.ok(page.map { CommentResponse.from(it) })
    }

    @GetMapping("/{commentId}/replies")
    fun getReplies(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ): ResponseEntity<List<CommentResponse>> {
        val replies = commentService.findReplies(commentId)
        return ResponseEntity.ok(replies.map { CommentResponse.from(it) })
    }

    @PutMapping("/{commentId}")
    fun update(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @RequestBody request: UpdateCommentRequest,
    ): ResponseEntity<CommentResponse> =
        commentService.update(request.toCommand(commentId)).toResponseEntity()

    @DeleteMapping("/{commentId}")
    fun delete(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @RequestParam author: String,
    ): ResponseEntity<Unit> =
        commentService.delete(commentId, author).toResponseEntity()
}