package com.caladon.worlds.resources

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Bounds how stale an un-viewed city can be (ARCHITECTURE.md → Buildings → Timers): every
 * `caladon.sweeper.interval-ms` (60 s, the ADVANCE_LAG) it advances every city that has a due order,
 * one short transaction per city, taking the city row lock with `SKIP LOCKED` so it never blocks a
 * player request and several instances can sweep at once. The map reads `city.points` as stored; this
 * lag is its only staleness. Resources need no sweeping (settled on read from the clocks).
 */
@Component
class CitySweeper(
    private val jdbc: NamedParameterJdbcTemplate,
    private val transactions: TransactionTemplate,
    private val cityAccess: CityAccess,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Advances every city with a due build or recruit order. Returns how many cities were advanced. */
    fun sweep(): Int {
        val now = clock.instant()
        val due = jdbc.queryForList(
            """
            SELECT city_id FROM city_build_order WHERE completes_at <= :now
            """,
            mapOf("now" to java.sql.Timestamp.from(now)),
            Long::class.java,
        )
        var advanced = 0
        for (batch in due.chunked(BATCH)) {
            for (cityId in batch) {
                val done = transactions.execute {
                    val locked = jdbc.queryForList(
                        "SELECT city_id FROM city_resources WHERE city_id = :id FOR UPDATE SKIP LOCKED",
                        mapOf("id" to cityId), Long::class.java,
                    )
                    if (locked.isEmpty()) false else { cityAccess.advanceCity(cityId); true }
                } ?: false
                if (done) advanced++
            }
        }
        if (due.isNotEmpty()) log.info("Sweep: {} of {} due cities advanced", advanced, due.size)
        return advanced
    }

    private companion object { const val BATCH = 100 }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty("caladon.sweeper.enabled", havingValue = "true", matchIfMissing = true)
class CitySweeperSchedule(private val sweeper: CitySweeper) {
    @Scheduled(fixedDelayString = "\${caladon.sweeper.interval-ms:60000}", initialDelayString = "\${caladon.sweeper.interval-ms:60000}")
    fun run() {
        try { sweeper.sweep() } catch (e: Exception) { LoggerFactory.getLogger(javaClass).warn("Sweep failed", e) }
    }
}
