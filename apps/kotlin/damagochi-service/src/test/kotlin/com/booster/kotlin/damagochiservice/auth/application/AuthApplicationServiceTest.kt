package com.booster.kotlin.damagochiservice.auth.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AuthApplicationServiceTest @Autowired constructor(
    private val authApplicationService: AuthApplicationService
) {
    @Test
    fun `signup and login success`() {
        val created = authApplicationService.signUp(
            SignUpCommand(
                loginId = "tester1",
                password = "pass1234",
                nickname = "tester"
            )
        )

        val loggedIn = authApplicationService.login(
            LoginCommand(
                loginId = "tester1",
                password = "pass1234"
            )
        )

        assertThat(loggedIn.userId).isEqualTo(created.userId)
        assertThat(loggedIn.loginId).isEqualTo("tester1")
    }

    @Test
    fun `duplicate login id is rejected`() {
        authApplicationService.signUp(
            SignUpCommand(
                loginId = "dup-user",
                password = "pass1234",
                nickname = "dup1"
            )
        )

        assertThatThrownBy {
            authApplicationService.signUp(
                SignUpCommand(
                    loginId = "dup-user",
                    password = "pass9999",
                    nickname = "dup2"
                )
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}


