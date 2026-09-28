package com.caladon.worlds.report

import com.caladon.worlds.service.WorldException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

/**
 * Writing reports and reading them back (ARCHITECTURE.md → Reports). A report belongs to one player: it
 * is never joined, never searched by its contents, and only ever listed newest first, so the payload is
 * one jsonb column and the whole thing is plain SQL.
 */
@Service
class ReportService(private val jdbc: NamedParameterJdbcTemplate, private val objectMapper: ObjectMapper) {

    enum class Kind { BATTLE, ESPIONAGE, ESPIONAGE_CAUGHT }

    /** One report; [payload] is filled only when the report is read on its own. */
    data class Row(
        val id: Long,
        val kind: Kind,
        val createdAt: Instant,
        val read: Boolean,
        val subjectCity: String,
        val otherCity: String?,
        val otherPlayer: String?,
        val won: Boolean?,
        val summary: String,
        /** Which side of it the reader was on, for a battle: the list draws an arrow from it. */
        val role: String?,
        val payload: JsonNode?,
    )

    /** [unread] counts the whole world, not the page or the kind filtered on: it is the top bar's number. */
    data class Page(val unread: Long, val total: Long, val limit: Int, val page: Int, val rows: List<Row>)

    /**
     * Writes one report and drops what retention no longer keeps. A report is a snapshot: everything it
     * says is passed in as it was at that moment, and nothing here is ever recomputed.
     */
    @Transactional
    fun write(
        worldId: Long,
        ownerUserId: Long,
        kind: Kind,
        createdAt: Instant,
        subjectCity: String,
        otherCity: String?,
        otherPlayer: String?,
        won: Boolean?,
        summary: String,
        payload: Any,
    ): Long {
        val id = requireNotNull(
            jdbc.queryForObject(
                """
                INSERT INTO report (world_id, owner_user_id, kind, created_at, subject_city, other_city,
                                    other_player, won, summary, payload)
                VALUES (:worldId, :ownerUserId, :kind, :createdAt, :subjectCity, :otherCity,
                        :otherPlayer, :won, :summary, CAST(:payload AS jsonb))
                RETURNING id
                """.trimIndent(),
                mapOf(
                    "worldId" to worldId, "ownerUserId" to ownerUserId, "kind" to kind.name,
                    "createdAt" to Timestamp.from(createdAt), "subjectCity" to subjectCity.take(64),
                    "otherCity" to otherCity?.take(64), "otherPlayer" to otherPlayer?.take(64), "won" to won,
                    "summary" to summary.take(255), "payload" to objectMapper.writeValueAsString(payload),
                ),
                Long::class.java,
            ),
        )
        retain(worldId, ownerUserId)
        return id
    }

    /**
     * Keeps the newest [KEEP] of this player's reports in this world, so the table cannot grow without
     * bound. The cut-off is the oldest report still kept, and everything older than it goes; before
     * there are that many there is no such row, and the comparison with null deletes nothing.
     */
    private fun retain(worldId: Long, ownerUserId: Long) {
        jdbc.update(
            """
            DELETE FROM report
            WHERE world_id = :worldId AND owner_user_id = :ownerUserId
              AND (created_at, id) < (
                SELECT created_at, id FROM report
                WHERE world_id = :worldId AND owner_user_id = :ownerUserId
                ORDER BY created_at DESC, id DESC OFFSET ${KEEP - 1} LIMIT 1
              )
            """.trimIndent(),
            mapOf("worldId" to worldId, "ownerUserId" to ownerUserId),
        )
    }

    /**
     * What the list may be narrowed to. A filter is a **group** rather than one kind, because a player
     * thinks of spying as one thing whether they did it or caught someone at it.
     */
    enum class Filter(val kinds: List<Kind>) {
        BATTLE(listOf(Kind.BATTLE)),
        SPYING(listOf(Kind.ESPIONAGE, Kind.ESPIONAGE_CAUGHT)),
    }

    /** One page of the player's own reports, newest first, with the unread count the top bar shows. */
    @Transactional(readOnly = true)
    fun list(worldId: Long, userId: Long, limit: Int, page: Int, filter: Filter?): Page {
        val size = limit.coerceIn(1, 100)
        val offset = (page.coerceAtLeast(1) - 1).toLong() * size
        val params = mapOf<String, Any?>(
            "worldId" to worldId, "userId" to userId,
            "kinds" to filter?.kinds?.map { it.name }?.toTypedArray(),
            "limit" to size, "offset" to offset,
        )
        val rows = jdbc.queryForList(
            """
            SELECT id, kind, created_at, read, subject_city, other_city, other_player, won, summary,
                   payload ->> 'role' AS role
            FROM report
            WHERE world_id = :worldId AND owner_user_id = :userId $OF_KIND
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        ).map { toRow(it, payload = null) }
        val total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM report WHERE world_id = :worldId AND owner_user_id = :userId $OF_KIND",
            params, Long::class.java,
        ) ?: 0
        val unread = jdbc.queryForObject(
            "SELECT COUNT(*) FROM report WHERE world_id = :worldId AND owner_user_id = :userId AND NOT read",
            mapOf("worldId" to worldId, "userId" to userId), Long::class.java,
        ) ?: 0
        return Page(unread = unread, total = total, limit = size, page = page.coerceAtLeast(1), rows = rows)
    }

    /**
     * One report, marked read by the reading. Ownership is part of the lookup, so asking for someone
     * else's is not a refusal but a report that does not exist.
     */
    @Transactional
    fun read(worldId: Long, userId: Long, id: Long): Row {
        val row = jdbc.queryForList(
            """
            UPDATE report SET read = TRUE
            WHERE id = :id AND world_id = :worldId AND owner_user_id = :userId
            RETURNING id, kind, created_at, read, subject_city, other_city, other_player, won, summary,
                      payload ->> 'role' AS role, payload
            """.trimIndent(),
            mapOf("id" to id, "worldId" to worldId, "userId" to userId),
        ).firstOrNull() ?: throw WorldException.ReportNotFound()
        return toRow(row, payload = objectMapper.readTree(row["payload"].toString()))
    }

    @Transactional
    fun delete(worldId: Long, userId: Long, id: Long) {
        val deleted = jdbc.update(
            "DELETE FROM report WHERE id = :id AND world_id = :worldId AND owner_user_id = :userId",
            mapOf("id" to id, "worldId" to worldId, "userId" to userId),
        )
        if (deleted == 0) throw WorldException.ReportNotFound()
    }

    private fun toRow(r: Map<String, Any?>, payload: JsonNode?) = Row(
        id = (r["id"] as Number).toLong(),
        kind = Kind.valueOf(r["kind"] as String),
        createdAt = (r["created_at"] as Timestamp).toInstant(),
        read = r["read"] as Boolean,
        subjectCity = r["subject_city"] as String,
        otherCity = r["other_city"] as String?,
        otherPlayer = r["other_player"] as String?,
        won = r["won"] as Boolean?,
        summary = r["summary"] as String,
        role = r["role"] as String?,
        payload = payload,
    )

    private companion object {
        /** The newest per player per world; older ones go as new ones arrive. */
        const val KEEP = 200
        const val OF_KIND = "AND (CAST(:kinds AS text[]) IS NULL OR kind = ANY(CAST(:kinds AS text[])))"
    }
}
