package com.booster.kotlin.boardservice.comment.application

import com.booster.kotlin.boardservice.comment.application.dto.CreateCommentCommand
import com.booster.kotlin.boardservice.comment.application.dto.UpdateCommentCommand
import com.booster.kotlin.boardservice.comment.domain.Comment
import com.booster.kotlin.boardservice.comment.domain.CommentDeleteResult
import com.booster.kotlin.boardservice.comment.domain.CommentRepository
import com.booster.kotlin.boardservice.comment.domain.CommentResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CommentService(
    private val commentRepository: CommentRepository,
) {

    fun create(command: CreateCommentCommand): CommentResult {
        if (command.parentId != null) {
            val parent = commentRepository.findById(command.parentId).orElse(null)
                ?: return CommentResult.NotFound(command.parentId)
            if (parent.isReply()) return CommentResult.InvalidParent(command.parentId)
            val reply = Comment.createReply(command.postId, command.content, command.author, command.parentId)
            return CommentResult.Success(commentRepository.save(reply))
        }
        val comment = Comment.create(command.postId, command.content, command.author)
        return CommentResult.Success(commentRepository.save(comment))
    }

    @Transactional(readOnly = true)
    fun findAllByPostId(postId: Long, pageable: Pageable): Page<Comment> {
        return commentRepository.findAllByPostIdAndParentIdIsNull(postId, pageable)
    }

    @Transactional(readOnly = true)
    fun findReplies(commentId: Long): List<Comment> {
        return commentRepository.findAllByParentId(commentId)
    }

    fun update(command: UpdateCommentCommand): CommentResult {
        val comment = commentRepository.findById(command.commentId).orElse(null)
            ?: return CommentResult.NotFound(command.commentId)
        if (comment.author != command.author) return CommentResult.Forbidden(command.commentId)
        comment.update(command.content)
        return CommentResult.Success(comment)
    }

    fun delete(commentId: Long, author: String): CommentDeleteResult {
        val comment = commentRepository.findById(commentId).orElse(null)
            ?: return CommentDeleteResult.NotFound(commentId)
        if (comment.author != author) return CommentDeleteResult.Forbidden(commentId)
        commentRepository.delete(comment)
        return CommentDeleteResult.Deleted
    }
}