package com.booster.kotlin.boardservice.post.domain

/**
 * Post 도메인 연산의 결과를 표현하는 Sealed Class
 *
 * 핵심 이점:
 * 1. 컴파일 타임 완전성 보장 — when 표현식에서 모든 케이스를 처리하지 않으면 컴파일 에러
 * 2. 예외 없는 흐름 — try-catch 대신 분기 처리로 의도를 명확하게 표현
 * 3. Smart Cast — is 검사 이후 캐스팅 없이 result.post 등 접근 가능
 */
sealed class PostResult {
    data class Success(val post: Post) : PostResult()
    data class NotFound(val id: Long) : PostResult()
}

/**
 * 삭제 연산의 결과
 * Success에 Post가 필요 없으므로 별도 sealed class로 분리
 * data object — 상태 없는 싱글턴 (Kotlin 1.9+)
 */
sealed class PostDeleteResult {
    data object Deleted : PostDeleteResult()
    data class NotFound(val id: Long) : PostDeleteResult()
}
