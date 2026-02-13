package com.booster.kotlin.damagochiservice.auth.web

import com.booster.kotlin.damagochiservice.auth.application.AuthApplicationService
import com.booster.kotlin.damagochiservice.auth.application.AuthUserView
import com.booster.kotlin.damagochiservice.auth.application.LoginCommand
import com.booster.kotlin.damagochiservice.auth.application.SignUpCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authApplicationService: AuthApplicationService
) {
    @PostMapping("/signup")
    fun signUp(
        @Valid @RequestBody request: SignUpRequest
    ): ResponseEntity<AuthResponse> {
        val user = authApplicationService.signUp(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(user))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<AuthResponse> {
        val user = authApplicationService.login(request.toCommand())
        return ResponseEntity.ok(AuthResponse.from(user))
    }
}

data class SignUpRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val loginId: String,
    @field:NotBlank
    @field:Size(min = 4, max = 100)
    val password: String,
    @field:NotBlank
    @field:Size(max = 30)
    val nickname: String
) {
    fun toCommand(): SignUpCommand = SignUpCommand(
        loginId = loginId,
        password = password,
        nickname = nickname
    )
}

data class LoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String
) {
    fun toCommand(): LoginCommand = LoginCommand(
        loginId = loginId,
        password = password
    )
}

data class AuthResponse(
    val userId: Long,
    val loginId: String,
    val nickname: String
) {
    companion object {
        fun from(view: AuthUserView): AuthResponse =
            AuthResponse(
                userId = view.userId,
                loginId = view.loginId,
                nickname = view.nickname
            )
    }
}




