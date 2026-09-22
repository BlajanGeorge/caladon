package com.caladon.worlds.api

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the tests can move forward, so lazy settlement can be exercised without sleeping. */
class MutableClock(@Volatile private var now: Instant = Instant.now()) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    fun advance(by: Duration) { now = now.plus(by) }
    fun reset() { now = Instant.now() }
}

@TestConfiguration
class MutableClockConfig {
    @Bean @Primary
    fun mutableClock(): MutableClock = MutableClock()
}
