package cu.ipvgc.domain.sync

import java.util.UUID

class SyncProcessor(
    private val store: SyncStore,
    var stage: SyncStage = SyncStage.A_COUNTS,
) {
    fun push(request: PushRequest): PushResponse {
        if (request.schemaVersion < SyncRules.CLIENT_SCHEMA) {
            return PushResponse(
                request.items.map {
                    MutationAck(it.mutationId, MutationResultStatus.REJECTED, RejectCodes.UPGRADE_REQUIRED)
                },
            )
        }
        val org = store.organizationOf(request.userId)
        val acks = request.items.sortedBy { it.seqNo }.map { item -> applyOne(request.userId, org, request.deviceId, item) }
        return PushResponse(acks)
    }

    fun pull(userId: UUID, cursor: Long, clientEpoch: Long, limit: Int = SyncRules.DEFAULT_PULL_LIMIT): PullResult {
        val org = store.organizationOf(userId)
        val epoch = store.epoch(org)
        if (clientEpoch != epoch) {
            return PullResult.ResyncRequired(epoch, "epoch mismatch")
        }
        if (cursor < store.minRetainedSeq(org)) {
            return PullResult.ResyncRequired(epoch, "cursor expired")
        }
        val changes = store.changesAfter(org, cursor, limit)
        val newCursor = changes.lastOrNull()?.seq ?: cursor
        return PullResult.Changes(epoch, changes, newCursor)
    }

    fun bootstrap(userId: UUID): Pair<Long, Long> {
        val org = store.organizationOf(userId)
        return store.epoch(org) to store.lastSeq(org)
    }

    private fun applyOne(userId: UUID, org: UUID, deviceId: UUID, item: PushItem): MutationAck {
        store.findMutation(item.mutationId)?.let { return it }

        if (!store.licenseOk(userId)) {
            return persist(item, org, deviceId, MutationAck(item.mutationId, MutationResultStatus.REJECTED, RejectCodes.LICENSE_INVALID))
        }
        if (!store.hasPermission(userId, SyncRules.permission(item.entityType))) {
            return persist(item, org, deviceId, MutationAck(item.mutationId, MutationResultStatus.REJECTED, RejectCodes.SCOPE_REVOKED))
        }
        if (!SyncRules.enabled(item.entityType, stage)) {
            return persist(item, org, deviceId, MutationAck(item.mutationId, MutationResultStatus.REJECTED, RejectCodes.STAGE_NOT_ENABLED))
        }
        if (item.payload["online_only"] == "true") {
            return persist(item, org, deviceId, MutationAck(item.mutationId, MutationResultStatus.REJECTED, RejectCodes.ONLINE_ONLY))
        }

        val existing = store.getEntity(org, item.entityType, item.entityId)
        val policy = SyncRules.policy(item.entityType)

        if (policy == ConflictPolicy.OPTIMISTIC_VERSION && existing != null && !existing.deleted) {
            val base = item.baseVersion
            if (base == null || base != existing.version) {
                val ack =
                    MutationAck(
                        mutationId = item.mutationId,
                        status = MutationResultStatus.CONFLICT,
                        resultCode = "VERSION_MISMATCH",
                        entityVersion = existing.version,
                        serverState = existing.payload,
                        clientPayload = item.payload,
                    )
                return persist(item, org, deviceId, ack)
            }
        }

        val nextVersion = (existing?.version ?: 0L) + 1
        val deleted = item.op == MutationOp.DELETE
        val row =
            EntityRow(
                organizationId = org,
                entityType = item.entityType,
                entityId = item.entityId,
                version = nextVersion,
                payload = if (deleted) existing?.payload ?: item.payload else item.payload,
                deleted = deleted,
            )
        store.putEntity(row)
        store.appendChange(
            Change(
                seq = 0,
                organizationId = org,
                entityType = item.entityType,
                entityId = item.entityId,
                op = item.op,
                entityVersion = nextVersion,
                payload = row.payload,
            ),
        )
        return persist(
            item,
            org,
            deviceId,
            MutationAck(item.mutationId, MutationResultStatus.APPLIED, "OK", nextVersion, row.payload, item.payload),
        )
    }

    private fun persist(item: PushItem, org: UUID, deviceId: UUID, ack: MutationAck): MutationAck {
        store.saveMutation(ack, item, org, deviceId)
        return ack
    }
}
