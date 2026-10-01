package cu.ipvgc.domain.sync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SyncChaosTest {
    private val org = UUID.fromString("11111111-1111-7000-8000-000000000001")
    private val user = UUID.fromString("11111111-1111-7000-8000-0000000000aa")
    private val device = UUID.fromString("11111111-1111-7000-8000-0000000000d1")
    private lateinit var store: InMemorySyncStore
    private lateinit var processor: SyncProcessor

    @BeforeEach
    fun setup() {
        store = InMemorySyncStore()
        store.addUser(
            InMemorySyncStore.User(user, org, mutableSetOf("inventory:move")),
        )
        processor = SyncProcessor(store, SyncStage.A_COUNTS)
    }

    @Test
    fun `oversized batch is rejected`() {
        val items =
            (1..(SyncRules.MAX_BATCH + 1)).map { n ->
                PushItem(
                    mutationId = UUID.randomUUID(),
                    seqNo = n.toLong(),
                    entityType = SyncEntityType.INVENTORY_COUNT,
                    entityId = UUID.randomUUID(),
                    op = MutationOp.UPSERT,
                    baseVersion = null,
                    payload = mapOf("n" to n.toString()),
                )
            }
        val res = processor.push(PushRequest(user, device, items = items))
        res.acks.all { it.status == MutationResultStatus.REJECTED && it.resultCode == RejectCodes.PAYLOAD_TOO_LARGE } shouldBe true
        store.lastSeq(org) shouldBe 0
    }

    @Test
    fun `oversized payload is rejected`() {
        val huge = "x".repeat(SyncRules.MAX_PAYLOAD_CHARS + 1)
        val id = UUID.randomUUID()
        val res =
            processor.push(
                PushRequest(
                    user,
                    device,
                    items =
                        listOf(
                            PushItem(id, 1, SyncEntityType.INVENTORY_COUNT, UUID.randomUUID(), MutationOp.UPSERT, null, mapOf("blob" to huge)),
                        ),
                ),
            )
        res.acks.single().resultCode shouldBe RejectCodes.PAYLOAD_TOO_LARGE
    }

    @Test
    fun `duplicate mutation id is idempotent`() {
        val mutation = UUID.randomUUID()
        val entity = UUID.randomUUID()
        val item = PushItem(mutation, 1, SyncEntityType.INVENTORY_COUNT, entity, MutationOp.UPSERT, null, mapOf("q" to "1"))
        processor.push(PushRequest(user, device, items = listOf(item)))
        val again = processor.push(PushRequest(user, device, items = listOf(item)))
        again.acks.single().status shouldBe MutationResultStatus.APPLIED
        store.lastSeq(org) shouldBe 1
    }

    @Test
    fun `wrong epoch forces RESYNC_REQUIRED`() {
        val pull = processor.pull(user, cursor = 0, clientEpoch = 99)
        (pull is PullResult.ResyncRequired) shouldBe true
    }
}
