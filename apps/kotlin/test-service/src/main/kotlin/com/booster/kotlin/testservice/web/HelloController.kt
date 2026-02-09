package com.booster.kotlin.testservice.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(): String {
        return "hello"
    }

    @GetMapping("/hi/{message}")
    fun hi(@PathVariable message: String): String {
        return "hi $message"
    }

    @PostMapping("/hello")
    fun helloBody(@RequestBody request: HelloRequest) : String{
        return "Hello ${request.message}"
    }

    data class HelloRequest(val message: String){

}

}