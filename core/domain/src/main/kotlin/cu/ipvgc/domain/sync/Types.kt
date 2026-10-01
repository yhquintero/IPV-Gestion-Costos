package cu.ipvgc.domain.sync

import java.util.UUID

enum class SyncStage {
    A_COUNTS,
    B_CONTROLS_MOVEMENTS,
    C_DRAFTS,
}

enum class SyncEntityType {
    INVENTORY_COUNT,
    INVENTORY_COUNT_LINE,
    INVENTORY_MOVEMENT,
    IPV_CONTROL_LINE,
    COST_SHEET_DRAFT,
}

enum class MutationOp { UPSERT, DELETE }

enum class MutationResultStatus { APPLIED, CONFLICT, REJECTED }

enum class OutboxState { PENDING, APPLIED, CONFLICT, REJECTED, STUCK }

enum class ConflictPolicy { APPEND_ONLY, OPTIMISTIC_VERSION }

object SyncRules {
    const val CLIENT_SCHEMA: Int = 1
    const val MAX_ATTEMPTS: Int = 8
    const val DEFAULT_PULL_LIMIT: Int = 200
    const val MAX_BATCH: Int = 100
    const val MAX_PAYLOAD_CHARS: Int = 8_192

    fun policy(type: SyncEntityType): ConflictPolicy =
        when (type) {
            SyncEntityType.INVENTORY_COUNT,
            SyncEntityType.INVENTORY_COUNT_LINE,
            SyncEntityType.INVENTORY_MOVEMENT,
            -> ConflictPolicy.APPEND_ONLY
            SyncEntityType.IPV_CONTROL_LINE,
            SyncEntityType.COST_SHEET_DRAFT,
            -> ConflictPolicy.OPTIMISTIC_VERSION
        }

    fun enabled(type: SyncEntityType, stage: SyncStage): Boolean =
        when (stage) {
            SyncStage.A_COUNTS ->
                type == SyncEntityType.INVENTORY_COUNT || type == SyncEntityType.INVENTORY_COUNT_LINE
            SyncStage.B_CONTROLS_MOVEMENTS ->
                enabled(type, SyncStage.A_COUNTS) ||
                    type == SyncEntityType.INVENTORY_MOVEMENT ||
                    type == SyncEntityType.IPV_CONTROL_LINE
            SyncStage.C_DRAFTS -> true
        }

    fun permission(type: SyncEntityType): String =
        when (type) {
            SyncEntityType.INVENTORY_COUNT,
            SyncEntityType.INVENTORY_COUNT_LINE,
            -> "inventory:move"
            SyncEntityType.INVENTORY_MOVEMENT -> "inventory:move"
            SyncEntityType.IPV_CONTROL_LINE -> "ipvcontrol:capture"
            SyncEntityType.COST_SHEET_DRAFT -> "costing:edit"
        }
}

data class PushItem(
    val mutationId: UUID,
    val seqNo: Long,
    val entityType: SyncEntityType,
    val entityId: UUID,
    val op: MutationOp,
    val baseVersion: Long?,
    val payload: Map<String, String>,
)

data class MutationAck(
    val mutationId: UUID,
    val status: MutationResultStatus,
    val resultCode: String? = null,
    val entityVersion: Long? = null,
    val serverState: Map<String, String>? = null,
    val clientPayload: Map<String, String>? = null,
)

data class Change(
    val seq: Long,
    val organizationId: UUID,
    val entityType: SyncEntityType,
    val entityId: UUID,
    val op: MutationOp,
    val entityVersion: Long,
    val payload: Map<String, String>,
)

data class EntityRow(
    val organizationId: UUID,
    val entityType: SyncEntityType,
    val entityId: UUID,
    val version: Long,
    val payload: Map<String, String>,
    val deleted: Boolean = false,
)

data class PushRequest(
    val userId: UUID,
    val deviceId: UUID,
    val schemaVersion: Int = SyncRules.CLIENT_SCHEMA,
    val items: List<PushItem>,
)

data class PushResponse(val acks: List<MutationAck>)

sealed class PullResult {
    data class Changes(val epoch: Long, val changes: List<Change>, val cursor: Long) : PullResult()
    data class ResyncRequired(val epoch: Long, val reason: String) : PullResult()
    data class UpgradeRequired(val minSchema: Int) : PullResult()
}

object RejectCodes {
    const val SCOPE_REVOKED = "SCOPE_REVOKED"
    const val LICENSE_INVALID = "LICENSE_INVALID"
    const val STAGE_NOT_ENABLED = "STAGE_NOT_ENABLED"
    const val ONLINE_ONLY = "ONLINE_ONLY"
    const val UPGRADE_REQUIRED = "UPGRADE_REQUIRED"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
}
