package com.booster.kotlin.shoppingservice.auth.application

import com.booster.kotlin.shoppingservice.auth.application.dto.LoginCommand
import com.booster.kotlin.shoppingservice.auth.application.dto.TokenResult
import com.booster.kotlin.shoppingservice.auth.domain.RefreshToken
import com.booster.kotlin.shoppingservice.auth.domain.RefreshTokenRepository
import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.config.jwt.JwtProvider
import com.booster.kotlin.shoppingservice.user.application.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    @param:Value("\${jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
    ) {

    fun login(command: LoginCommand): TokenResult {
        val user = userService.getByEmail(command.email)
        if(!passwordEncoder.matches(command.password,user.passwordHash)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        val accessToken = jwtProvider.generateAccessToken(user.id,user.role)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(
            RefreshToken(
                userId = user.id,
                token = refreshToken,
                ttl = refreshTokenExpiry / 1000
            )
        )
        return TokenResult(accessToken = accessToken,refreshToken = refreshToken)
    }

    fun refresh(refreshToken: String): TokenResult {
        val userId = jwtProvider.getUserId(refreshToken)
        val stored = refreshTokenRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.INVALID_TOKEN) }
        if (stored.token != refreshToken) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
        val user = userService.getById(userId)
        val newAccessToken = jwtProvider.generateAccessToken(user.id, user.role)
        val newRefreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(stored.copy(token = newRefreshToken))
        return TokenResult(accessToken = newAccessToken, refreshToken = newRefreshToken)
    }

    fun logout(userId: Long) {
        refreshTokenRepository.deleteById(userId)
    }

}