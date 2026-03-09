package com.booster.kotlin.boardservice.comment.web.controller

import com.booster.kotlin.boardservice.comment.application.CommentService
import com.booster.kotlin.boardservice.comment.web.dto.request.CreateCommentRequest
import com.booster.kotlin.boardservice.comment.web.dto.request.UpdateCommentRequest
import com.booster.kotlin.boardservice.comment.web.dto.response.CommentResponse
import com.booster.kotlin.boardservice.comment.web.toResponseEntity
import org.springframework.http.HttpStatus
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
    ): ResponseEntity<CommentResponse> {
        val comment = commentService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment))
    }

    @GetMapping
    fun getAll(@PathVariable postId: Long): ResponseEntity<List<CommentResponse>> {
        val comments = commentService.findAllByPostId(postId)
        return ResponseEntity.ok(comments.map { CommentResponse.from(it) })
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