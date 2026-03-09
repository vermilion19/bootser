package com.booster.kotlin.boardservice.comment.application

import com.booster.kotlin.boardservice.comment.application.dto.CreateCommentCommand
import com.booster.kotlin.boardservice.comment.application.dto.UpdateCommentCommand
import com.booster.kotlin.boardservice.comment.domain.Comment
import com.booster.kotlin.boardservice.comment.domain.CommentDeleteResult
import com.booster.kotlin.boardservice.comment.domain.CommentRepository
import com.booster.kotlin.boardservice.comment.domain.CommentResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CommentService(
    private val commentRepository: CommentRepository,
) {

    fun create(command: CreateCommentCommand): Comment {
        return commentRepository.save(
            Comment.create(command.postId,command.content,command.author)
        )
    }

    fun findAllByPostId(postId: Long): List<Comment> {
        return commentRepository.findAllByPostId(postId)
    }

    fun update(command: UpdateCommentCommand): CommentResult {
        val comment = commentRepository.findById(command.commentId).orElse(null)
            ?: return CommentResult.NotFound(command.commentId)
        if(comment.author != command.author) return CommentResult.Forbidden(command.commentId)
        comment.update(command.content)
        return CommentResult.Success(comment)
    }

    fun delete(commentId: Long, author: String): CommentDeleteResult {
        val comment = commentRepository.findById(commentId).orElse(null)
            ?: return CommentDeleteResult.NotFound(commentId)
        if(comment.author != author) return CommentDeleteResult.Forbidden(commentId)
        commentRepository.delete(comment)
        return CommentDeleteResult.Deleted
    }
}