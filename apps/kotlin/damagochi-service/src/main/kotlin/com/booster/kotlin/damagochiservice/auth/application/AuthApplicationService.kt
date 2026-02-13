package com.booster.kotlin.damagochiservice.auth.application

import com.booster.kotlin.damagochiservice.auth.domain.AppUser
import com.booster.kotlin.damagochiservice.auth.domain.AppUserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthApplicationService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    @Transactional
    fun signUp(command: SignUpCommand): AuthUserView {
        require(!appUserRepository.existsByLoginId(command.loginId)) {
            "Login id already exists: ${command.loginId}"
        }

        val user = appUserRepository.save(
            AppUser.create(
                loginId = command.loginId,
                passwordHash = requireNotNull(passwordEncoder.encode(command.password)) {
                    "Password encoding failed"
                },
                nickname = command.nickname
            )
        )
        return AuthUserView.from(user)
    }

    fun login(command: LoginCommand): AuthUserView {
        val user = appUserRepository.findByLoginId(command.loginId)
            ?: throw IllegalArgumentException("Invalid loginId or password")

        val valid = user.matchesPassword(command.password, passwordEncoder::matches)
        require(valid) { "Invalid loginId or password" }
        return AuthUserView.from(user)
    }
}

data class SignUpCommand(
    val loginId: String,
    val password: String,
    val nickname: String
)

data class LoginCommand(
    val loginId: String,
    val password: String
)

data class AuthUserView(
    val userId: Long,
    val loginId: String,
    val nickname: String
) {
    companion object {
        fun from(user: AppUser): AuthUserView =
            AuthUserView(
                userId = user.id,
                loginId = user.loginId,
                nickname = user.nickname
            )
    }
}




