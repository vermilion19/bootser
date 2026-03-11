package com.booster.kotlin.shoppingservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShoppingServiceApplication

fun main(args: Array<String>) {
    runApplication<ShoppingServiceApplication>(*args)
}
