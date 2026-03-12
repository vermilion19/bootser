package com.booster.kotlin.shoppingservice.config.jwt

import com.booster.kotlin.shoppingservice.common.exception.BusinessException
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.user.domain.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @param:Value("\${jwt.secret}") private val secret: String,
    @param:Value("\${jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @param:Value("\${jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
    }

    fun generateAccessToken(userId: Long, role: Role): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(Date(now.time + accessTokenExpiry))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + refreshTokenExpiry))
            .signWith(key)
            .compact()
    }

    fun getUserId(token: String): Long = parseClaims(token).subject.toLong()

    fun getRole(token: String): Role =
        Role.valueOf(parseClaims(token).get("role", String::class.java))

    private fun parseClaims(token: String): Claims {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.EXPIRED_TOKEN)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
    }


}