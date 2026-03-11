package com.booster.kotlin.shoppingservice.user.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.user.application.dto.CreateUserCommand
import com.booster.kotlin.shoppingservice.user.application.dto.UpdateUserCommand
import com.booster.kotlin.shoppingservice.user.domain.User
import com.booster.kotlin.shoppingservice.user.domain.UserRepository
import com.booster.kotlin.shoppingservice.user.exception.UserException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService (
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    ){

    fun signup(command: CreateUserCommand): User {
        if (userRepository.existsByEmail(command.email)) {
            throw UserException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }
        val user = User.create(
            email = command.email,
            passwordHash = passwordEncoder.encode(command.password)!!,
            name = command.name,
            phone = command.phone,
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun getById(userId: Long): User {
        return userRepository.findById(userId).orElseThrow {
            UserException(ErrorCode.USER_NOT_FOUND)
        }
    }

    @Transactional(readOnly = true)
    fun getByEmail(email: String): User {
        return userRepository.findByEmail(email)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)
    }

    fun updateProfile(command: UpdateUserCommand): User {
        val user = getById(command.userId)
        user.updateProfile(name = command.name, phone = command.phone)
        return user
    }

}