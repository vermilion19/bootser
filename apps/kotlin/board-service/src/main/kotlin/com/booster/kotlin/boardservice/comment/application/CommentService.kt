package com.booster.kotlin.boardservice.comment.application

import com.booster.kotlin.boardservice.comment.application.dto.CreateCommentCommand
import com.booster.kotlin.boardservice.comment.application.dto.CursorResult
import com.booster.kotlin.boardservice.comment.application.dto.UpdateCommentCommand
import com.booster.kotlin.boardservice.comment.domain.Comment
import com.booster.kotlin.boardservice.comment.domain.CommentDeleteResult
import com.booster.kotlin.boardservice.comment.domain.CommentRepository
import com.booster.kotlin.boardservice.comment.domain.CommentResult
import com.booster.kotlin.boardservice.config.CacheNames
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CommentService(
    private val commentRepository: CommentRepository,
) {

    @CacheEvict(
        cacheNames = [CacheNames.COMMENTS],
        key = "#command.postId + '_0'",
    )
    fun create(command: CreateCommentCommand): CommentResult {
        val parentId: Long? = command.parentId
        if (parentId != null) {
            val parent = commentRepository.findByIdOrNull(parentId)
                ?: return CommentResult.NotFound(parentId)
            if (parent.isReply()) return CommentResult.InvalidParent(parentId)
            return Comment.createReply(command.postId, command.content, command.author, parentId)
                .let { commentRepository.save(it) }
                .let { CommentResult.Success(it) }
        }
        return Comment.create(command.postId, command.content, command.author)
            .let { commentRepository.save(it) }
            .let { CommentResult.Success(it) }
    }

    @Cacheable(
        cacheNames = [CacheNames.COMMENTS],
        key = "#postId + '_' + #pageable.pageNumber"
        )
    @Transactional(readOnly = true)
    fun findAllByPostId(postId: Long, pageable: Pageable): Page<Comment> {
        return commentRepository.findAllByPostIdAndParentIdIsNull(postId, pageable)
    }

    @Transactional(readOnly = true)
    fun findReplies(commentId: Long): List<Comment> {
        return commentRepository.findAllByParentId(commentId)
    }

    fun update(command: UpdateCommentCommand): CommentResult {
        val comment = commentRepository.findByIdOrNull(command.commentId)
            ?: return CommentResult.NotFound(command.commentId)
        if (comment.author != command.author) return CommentResult.Forbidden(command.commentId)
        comment.update(command.content)
        return CommentResult.Success(comment)
    }

    fun delete(commentId: Long, author: String): CommentDeleteResult {
        val comment = commentRepository.findByIdOrNull(commentId)
            ?: return CommentDeleteResult.NotFound(commentId)
        if (comment.author != author) return CommentDeleteResult.Forbidden(commentId)
        return comment
            .also { commentRepository.delete(it) }
            .let { CommentDeleteResult.Deleted }
    }

    fun findByPostIdWithCursor(postId: Long, lastId: Long, size: Int): CursorResult {
        val pageable = PageRequest.of(0,size)
        val comments = commentRepository
            .findByPostIdAndParentIdIsNullAndIdLessThanOrderByIdDesc(postId, lastId,pageable)

        val nextCursor = if(comments.size == size) comments.last().id else null
        return CursorResult(comments,nextCursor,nextCursor != null)
    }
}