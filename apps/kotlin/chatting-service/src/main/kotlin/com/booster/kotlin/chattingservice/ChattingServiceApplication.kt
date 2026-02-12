package com.booster.kotlin.chattingservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChattingServiceApplication

fun main(args: Array<String>) {
    runApplication<ChattingServiceApplication>(*args)
}
