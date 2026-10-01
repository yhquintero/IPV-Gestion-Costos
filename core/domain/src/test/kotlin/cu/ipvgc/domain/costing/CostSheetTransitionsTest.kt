package cu.ipvgc.domain.costing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CostSheetTransitionsTest {
    @Test
    fun `happy path draft to vigente`() {
        CostSheetTransitions.canTransition(CostSheetStatus.BORRADOR, CostSheetStatus.EN_REVISION) shouldBe true
        CostSheetTransitions.canTransition(CostSheetStatus.EN_REVISION, CostSheetStatus.VALIDADA) shouldBe true
        CostSheetTransitions.canTransition(CostSheetStatus.VALIDADA, CostSheetStatus.APROBADA) shouldBe true
        CostSheetTransitions.canTransition(CostSheetStatus.APROBADA, CostSheetStatus.VIGENTE) shouldBe true
        CostSheetTransitions.canTransition(CostSheetStatus.VIGENTE, CostSheetStatus.REEMPLAZADA) shouldBe true
    }

    @Test
    fun `cannot skip or resurrect`() {
        CostSheetTransitions.canTransition(CostSheetStatus.BORRADOR, CostSheetStatus.VIGENTE) shouldBe false
        CostSheetTransitions.canTransition(CostSheetStatus.ANULADA, CostSheetStatus.BORRADOR) shouldBe false
        CostSheetTransitions.canTransition(CostSheetStatus.REEMPLAZADA, CostSheetStatus.VIGENTE) shouldBe false
        shouldThrow<IllegalArgumentException> {
            CostSheetTransitions.requireTransition(CostSheetStatus.BORRADOR, CostSheetStatus.ANULADA)
        }
    }

    @Test
    fun `frozen flag matches I-05`() {
        CostSheetStatus.BORRADOR.isFrozen shouldBe false
        CostSheetStatus.VIGENTE.isFrozen shouldBe true
        CostSheetStatus.ANULADA.allowsControlLine shouldBe false
        CostSheetStatus.REEMPLAZADA.allowsControlLine shouldBe true
    }
}
