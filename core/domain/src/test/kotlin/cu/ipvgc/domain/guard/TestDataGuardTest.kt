package cu.ipvgc.domain.guard

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class TestDataGuardTest {
    @Test
    fun `production refuses test rate samples`() {
        shouldThrow<IllegalStateException> {
            TestDataGuard.assertProductionHasNoTestRates("prod", 1)
        }
    }

    @Test
    fun `dev and empty counts are allowed`() {
        TestDataGuard.assertProductionHasNoTestRates("dev", 7)
        TestDataGuard.assertProductionHasNoTestRates("prod", 0)
    }
}
