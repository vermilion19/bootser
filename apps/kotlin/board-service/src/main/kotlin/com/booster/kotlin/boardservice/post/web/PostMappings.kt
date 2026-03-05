package com.booster.kotlin.boardservice.post.web

import com.booster.kotlin.boardservice.post.domain.Post
import com.booster.kotlin.boardservice.post.domain.PostDeleteResult
import com.booster.kotlin.boardservice.post.domain.PostResult
import com.booster.kotlin.boardservice.post.domain.PostSummary
import com.booster.kotlin.boardservice.post.infrastructure.PostRow
import com.booster.kotlin.boardservice.post.web.dto.response.PostResponse
import com.booster.kotlin.boardservice.post.web.dto.response.PostSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity

// =============================================================================
// Post → PostResponse
// 확장 함수: 기존 클래스를 수정하지 않고 기능을 추가
// 레이어 원칙: domain → web 변환이므로 web 패키지에 위치
// =============================================================================

fun Post.toResponse() = PostResponse(id, title, content, author)

// 컬렉션 확장 함수 — posts.toResponse() 처럼 chaining 가능
fun List<Post>.toResponse() = map { it.toResponse() }

// Page 확장 함수 — Page<T>의 map()이 Page<R>를 반환하므로 그대로 활용
fun Page<Post>.toResponse(): Page<PostResponse> = map { it.toResponse() }

// =============================================================================
// PostSummary (Interface Projection) → PostSummaryResponse
// =============================================================================

fun PostSummary.toSummaryResponse() = PostSummaryResponse(id, title, author)

// =============================================================================
// PostRow (JDBC 결과) → PostSummaryResponse
// =============================================================================

fun PostRow.toSummaryResponse() = PostSummaryResponse(id, title, author, contentPreview)

// =============================================================================
// PostResult (Sealed Class) → ResponseEntity
// Sealed class에 확장 함수를 붙여 컨트롤러의 when 분기를 위임
// → 컨트롤러는 "결과를 ResponseEntity로 변환한다"는 의도만 표현
// =============================================================================

fun PostResult.toResponseEntity(): ResponseEntity<PostResponse> =
    when (this) {
        is PostResult.Success  -> ResponseEntity.ok(post.toResponse())
        is PostResult.NotFound -> ResponseEntity.notFound().build()
    }

fun PostDeleteResult.toResponseEntity(): ResponseEntity<Unit> =
    when (this) {
        is PostDeleteResult.Deleted  -> ResponseEntity.noContent().build()
        is PostDeleteResult.NotFound -> ResponseEntity.notFound().build()
    }
