package com.booster.kotlin.boardservice.comment.web

import com.booster.kotlin.boardservice.comment.domain.CommentDeleteResult
import com.booster.kotlin.boardservice.comment.domain.CommentResult
import com.booster.kotlin.boardservice.comment.web.dto.response.CommentResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

fun CommentResult.toResponseEntity(): ResponseEntity<CommentResponse> = when (this) {
    is CommentResult.Success -> ResponseEntity.ok(CommentResponse.from(comment))
    is CommentResult.NotFound -> ResponseEntity.notFound().build()
    is CommentResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
}

fun CommentDeleteResult.toResponseEntity(): ResponseEntity<Unit> = when (this) {
    is CommentDeleteResult.Deleted -> ResponseEntity.noContent().build()
    is CommentDeleteResult.NotFound -> ResponseEntity.notFound().build()
    is CommentDeleteResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
}
