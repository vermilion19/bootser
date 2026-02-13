package com.booster.kotlin.damagochiservice

import com.booster.kotlin.damagochiservice.common.config.GameBalanceProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(GameBalanceProperties::class)
class DamagochiServiceApplication

fun main(args: Array<String>) {
    runApplication<DamagochiServiceApplication>(*args)
}



