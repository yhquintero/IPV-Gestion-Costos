package cu.ipvgc.seed

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.File

class TestRatesFileTest {
    @Test
    fun `rates come from JSON and are not hardcoded in the generator`() {
        val file = SyntheticSeed.loadTestRates()
        file.instruments.size shouldBe 7
        file.instruments shouldContainKey "USD"
        file.instruments["USD"] shouldBe "755.00"

        val generator = File("src/main/kotlin/cu/ipvgc/seed/SyntheticSeed.kt").readText()
        generator.shouldNotContain("755.00")
        generator.shouldNotContain("850.00")
        generator.shouldNotContain("492.76")
    }
}
