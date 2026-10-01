package cu.ipvgc.domain.sync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SyncScenariosTest {
    private val org = UUID.fromString("11111111-1111-7000-8000-000000000001")
    private val userA = UUID.fromString("11111111-1111-7000-8000-0000000000aa")
    private val userB = UUID.fromString("11111111-1111-7000-8000-0000000000bb")
    private val deviceA = UUID.fromString("11111111-1111-7000-8000-0000000000d1")
    private val deviceB = UUID.fromString("11111111-1111-7000-8000-0000000000d2")

    private lateinit var store: InMemorySyncStore
    private lateinit var processor: SyncProcessor

    @BeforeEach
    fun setup() {
        store = InMemorySyncStore()
        store.addUser(
            InMemorySyncStore.User(
                id = userA,
                organizationId = org,
                permissions = mutableSetOf("inventory:move", "costing:edit"),
            ),
        )
        store.addUser(
            InMemorySyncStore.User(
                id = userB,
                organizationId = org,
                permissions = mutableSetOf("inventory:move", "costing:edit"),
            ),
        )
        processor = SyncProcessor(store, SyncStage.C_DRAFTS)
    }

    @Test
    fun `E-1 disconnect then reconnect applies each mutation once in order`() {
        val client = SyncClient(userA, deviceA, processor)
        val ids = (1..5).map { n ->
            val id = UUID.randomUUID()
            client.outbox.enqueue(
                SyncEntityType.INVENTORY_COUNT,
                id,
                MutationOp.UPSERT,
                mapOf("n" to n.toString(), "observed_qty" to "2"),
            )
            id
        }
        client.outbox.pendingCount() shouldBe 5
        client.cycle() shouldBe "OK"
        client.outbox.pendingCount() shouldBe 0
        ids.forEach { store.getEntity(org, SyncEntityType.INVENTORY_COUNT, it)!!.version shouldBe 1 }
        store.lastSeq(org) shouldBe 5
        client.replica.rows.size shouldBe 5
    }

    @Test
    fun `E-2 concurrent edits of same base version yield one APPLIED and one CONFLICT`() {
        processor.stage = SyncStage.C_DRAFTS
        val entity = UUID.fromString("11111111-1111-7000-8000-0000000000e2")
        val a = SyncClient(userA, deviceA, processor)
        val b = SyncClient(userB, deviceB, processor)
        a.outbox.enqueue(
            SyncEntityType.COST_SHEET_DRAFT,
            entity,
            MutationOp.UPSERT,
            mapOf("note" to "from-A"),
            baseVersion = 0,
        )
        a.cycle()
        val v1 = store.getEntity(org, SyncEntityType.COST_SHEET_DRAFT, entity)!!.version
        v1 shouldBe 1
        a.outbox.enqueue(
            SyncEntityType.COST_SHEET_DRAFT,
            entity,
            MutationOp.UPSERT,
            mapOf("note" to "A-v2"),
            baseVersion = v1,
        )
        b.outbox.enqueue(
            SyncEntityType.COST_SHEET_DRAFT,
            entity,
            MutationOp.UPSERT,
            mapOf("note" to "B-v2"),
            baseVersion = v1,
        )
        a.cycle()
        b.cycle()
        val states = listOf(a, b).flatMap { it.outbox.all() }.map { it.state }
        states.count { it == OutboxState.CONFLICT } shouldBe 1
        states.count { it == OutboxState.APPLIED } shouldBe 2 // create + one edit
        val conflict = listOf(a, b).flatMap { it.outbox.conflicts() }.single()
        conflict.lastError shouldBe "VERSION_MISMATCH"
        store.getEntity(org, SyncEntityType.COST_SHEET_DRAFT, entity)!!.payload["note"] shouldBe "A-v2"
        conflict.mutation.payload["note"] shouldBe "B-v2"
    }

    @Test
    fun `E-3 revoked permission rejects offline mutations and does not apply them`() {
        val client = SyncClient(userA, deviceA, processor)
        val id = UUID.randomUUID()
        client.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, id, MutationOp.UPSERT, mapOf("observed_qty" to "1"))
        store.user(userA).permissions.clear()
        client.cycle()
        client.outbox.all().single().state shouldBe OutboxState.REJECTED
        client.outbox.all().single().lastError shouldBe RejectCodes.SCOPE_REVOKED
        store.getEntity(org, SyncEntityType.INVENTORY_COUNT, id) shouldBe null
    }

    @Test
    fun `E-3 license invalid is rejected`() {
        val client = SyncClient(userA, deviceA, processor)
        val id = UUID.randomUUID()
        client.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, id, MutationOp.UPSERT, mapOf("q" to "1"))
        store.user(userA).licenseOk = false
        client.cycle()
        client.outbox.all().single().lastError shouldBe RejectCodes.LICENSE_INVALID
        store.getEntity(org, SyncEntityType.INVENTORY_COUNT, id) shouldBe null
    }

    @Test
    fun `E-4 identical mutation_id is applied once and acks match`() {
        val client = SyncClient(userA, deviceA, processor)
        val mutationId = UUID.fromString("11111111-1111-7000-8000-0000000000e4")
        val entity = UUID.randomUUID()
        repeat(5) {
            // re-push the same pending item
            if (client.outbox.pending().isEmpty()) {
                client.outbox.enqueue(
                    SyncEntityType.INVENTORY_COUNT,
                    entity,
                    MutationOp.UPSERT,
                    mapOf("q" to "3"),
                    mutationId = mutationId,
                )
            }
            val first = processor.push(
                PushRequest(userA, deviceA, items = listOf(client.outbox.pending().first().mutation)),
            )
            val again = processor.push(
                PushRequest(userA, deviceA, items = listOf(client.outbox.pending().first().mutation)),
            )
            first.acks.single() shouldBe again.acks.single()
            first.acks.single().status shouldBe MutationResultStatus.APPLIED
        }
        store.lastSeq(org) shouldBe 1
    }

    @Test
    fun `E-5 restore bumps epoch, client RESYNC_REQUIRED keeps pending and does not duplicate`() {
        val client = SyncClient(userA, deviceA, processor)
        val appliedId = UUID.randomUUID()
        client.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, appliedId, MutationOp.UPSERT, mapOf("q" to "1"))
        client.cycle()
        client.outbox.pendingCount() shouldBe 0
        val pendingId = UUID.randomUUID()
        client.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, pendingId, MutationOp.UPSERT, mapOf("q" to "9"))
        store.restoreBackup(org, keepLast = 0)
        val result = client.cycle()
        result shouldBe "RESYNC"
        store.getEntity(org, SyncEntityType.INVENTORY_COUNT, pendingId)!!.payload["q"] shouldBe "9"
        // mutation_id of the pending item applied exactly once after resync
        store.lastSeq(org) shouldBe 1
        client.outbox.pendingCount() shouldBe 0
    }

    @Test
    fun `stage 6a rejects cost sheet drafts`() {
        processor.stage = SyncStage.A_COUNTS
        val client = SyncClient(userA, deviceA, processor)
        client.outbox.enqueue(
            SyncEntityType.COST_SHEET_DRAFT,
            UUID.randomUUID(),
            MutationOp.UPSERT,
            mapOf("note" to "no"),
        )
        client.cycle()
        client.outbox.all().single().lastError shouldBe RejectCodes.STAGE_NOT_ENABLED
    }

    @Test
    fun `append-only counts never conflict`() {
        processor.stage = SyncStage.A_COUNTS
        val a = SyncClient(userA, deviceA, processor)
        val b = SyncClient(userB, deviceB, processor)
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        a.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, idA, MutationOp.UPSERT, mapOf("item" to "HARINA"))
        b.outbox.enqueue(SyncEntityType.INVENTORY_COUNT, idB, MutationOp.UPSERT, mapOf("item" to "HARINA"))
        a.cycle()
        b.cycle()
        a.outbox.conflicts() shouldHaveSize 0
        b.outbox.conflicts() shouldHaveSize 0
        store.lastSeq(org) shouldBe 2
    }

    @Test
    fun `attempts beyond cap mark STUCK`() {
        val box = ClientOutbox()
        val item = box.enqueue(SyncEntityType.INVENTORY_COUNT, UUID.randomUUID(), MutationOp.UPSERT, mapOf("q" to "1"))
        repeat(SyncRules.MAX_ATTEMPTS) { box.markAttempt(item.mutation.mutationId) }
        box.all().single().state shouldBe OutboxState.STUCK
    }
}
