package com.caladon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

// Authentication is JWT-only (see the users module); Spring's default in-memory user is not wanted.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class CaladonApplication

fun main(args: Array<String>) {
    runApplication<CaladonApplication>(*args)
}
