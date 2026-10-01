package cu.ipvgc.server.sync

import cu.ipvgc.domain.sync.MutationOp
import cu.ipvgc.domain.sync.PullResult
import cu.ipvgc.domain.sync.PushItem
import cu.ipvgc.domain.sync.PushRequest
import cu.ipvgc.domain.sync.SyncEntityType
import cu.ipvgc.domain.sync.SyncProcessor
import cu.ipvgc.domain.sync.SyncRules
import cu.ipvgc.domain.sync.SyncStage
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val jdbc: JdbcTemplate,
    @Value("\${ipvgc.sync.stage:A_COUNTS}") private val stageName: String,
) {
    private fun processor(): SyncProcessor {
        val user = currentUser() ?: throw ApiException.unauthorized()
        val stage = runCatching { SyncStage.valueOf(stageName) }.getOrDefault(SyncStage.A_COUNTS)
        return SyncProcessor(JdbcSyncStore(jdbc, user), stage)
    }

    @PostMapping("/push")
    @Transactional
    fun push(
        @RequestHeader("X-Device-Id") deviceId: UUID,
        @RequestHeader("X-Client-Schema", required = false, defaultValue = "1") schema: Int,
        @RequestBody body: PushBody,
    ): Map<String, Any?> {
        val user = currentUser() ?: throw ApiException.unauthorized()
        val items = body.mutations.map {
            PushItem(
                mutationId = it.mutation_id,
                seqNo = it.seq_no,
                entityType = SyncEntityType.valueOf(it.entity_type),
                entityId = it.entity_id,
                op = MutationOp.valueOf(it.op),
                baseVersion = it.base_version,
                payload = it.payload,
            )
        }
        val res = processor().push(PushRequest(user.userId, deviceId, schema, items))
        return mapOf(
            "acks" to res.acks.map { ack ->
                mapOf(
                    "mutation_id" to ack.mutationId,
                    "status" to ack.status.name,
                    "result_code" to ack.resultCode,
                    "entity_version" to ack.entityVersion,
                    "server_state" to ack.serverState,
                )
            },
        )
    }

    @GetMapping("/changes")
    fun changes(
        @RequestParam cursor: Long = 0,
        @RequestParam epoch: Long = 1,
    ): Map<String, Any?> {
        currentUser() ?: throw ApiException.unauthorized()
        return when (val pull = processor().pull(currentUser()!!.userId, cursor, epoch)) {
            is PullResult.ResyncRequired ->
                mapOf("status" to "RESYNC_REQUIRED", "epoch" to pull.epoch, "reason" to pull.reason)
            is PullResult.UpgradeRequired ->
                mapOf("status" to "UPGRADE_REQUIRED", "min_schema" to pull.minSchema)
            is PullResult.Changes ->
                mapOf(
                    "status" to "OK",
                    "epoch" to pull.epoch,
                    "cursor" to pull.cursor,
                    "changes" to pull.changes.map {
                        mapOf(
                            "seq" to it.seq,
                            "entity_type" to it.entityType.name,
                            "entity_id" to it.entityId,
                            "op" to it.op.name,
                            "entity_version" to it.entityVersion,
                            "payload" to it.payload,
                        )
                    },
                )
        }
    }

    @GetMapping("/bootstrap")
    fun bootstrap(): Map<String, Any?> {
        val user = currentUser() ?: throw ApiException.unauthorized()
        val (epoch, cursor) = processor().bootstrap(user.userId)
        return mapOf(
            "epoch" to epoch,
            "cursor" to cursor,
            "schema" to SyncRules.CLIENT_SCHEMA,
            "note" to "Instantánea vía /sync/changes desde cursor 0. Conservar outbox PENDING.",
        )
    }
}

data class PushBody(val mutations: List<PushMutationDto> = emptyList())

data class PushMutationDto(
    val mutation_id: UUID,
    val seq_no: Long,
    val entity_type: String,
    val entity_id: UUID,
    val op: String,
    val base_version: Long? = null,
    val payload: Map<String, String> = emptyMap(),
)
