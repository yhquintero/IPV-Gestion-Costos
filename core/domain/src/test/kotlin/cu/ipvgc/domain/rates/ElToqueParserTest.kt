package cu.ipvgc.domain.rates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ElToqueParserTest {
    @Test
    fun `community fixture maps ECU to EUR and records unknown keys`() {
        val r = ElToqueParser.parse(SimulatedElToqueHttp.COMMUNITY_OK)
        assertEquals(ProviderOutcome.OK, r.outcome)
        assertEquals(setOf("USD", "EUR", "MLC"), r.quotes.map { it.instrument.code }.toSet())
        assertEquals(0, BigDecimal("442.0").compareTo(r.quotes.first { it.instrument.code == "USD" }.value))
        assertEquals(0, BigDecimal("500.0").compareTo(r.quotes.first { it.instrument.code == "EUR" }.value))
        assertTrue(r.unknownKeys.containsAll(listOf("USDT_TRC20", "BTC", "BNB", "TRX")))
        assertEquals("2025-10-04", r.sourceTsRaw?.get("date"))
    }

    @Test
    fun `does not invent CAD MXN ZELLE CLA`() {
        val r = ElToqueParser.parse(SimulatedElToqueHttp.COMMUNITY_OK)
        val codes = r.quotes.map { it.instrument.code }
        assertTrue(listOf("CAD", "MXN", "ZELLE", "CLA").none { it in codes })
    }

    @Test
    fun `html challenge is invalid payload`() {
        val r = ElToqueParser.parse(SimulatedElToqueHttp.HTML_CHALLENGE, "text/html")
        assertEquals(ProviderOutcome.INVALID_PAYLOAD, r.outcome)
        assertEquals("html_or_challenge", r.error)
    }

    @Test
    fun `truncated json is invalid`() {
        assertEquals(ProviderOutcome.INVALID_PAYLOAD, ElToqueParser.parse(SimulatedElToqueHttp.TRUNCATED).outcome)
    }

    @Test
    fun `negative and zero are rejected`() {
        assertEquals(ProviderOutcome.INVALID_PAYLOAD, ElToqueParser.parse(SimulatedElToqueHttp.NEGATIVE).outcome)
        assertEquals(ProviderOutcome.INVALID_PAYLOAD, ElToqueParser.parse(SimulatedElToqueHttp.ZERO).outcome)
    }

    @Test
    fun `stored value equals received text`() {
        val r = ElToqueParser.parse("""{"tasas":{"USD":755.00,"ECU":850.00,"MLC":492.76}}""")
        assertEquals(0, BigDecimal("755.00").compareTo(r.quotes.first { it.instrument.code == "USD" }.value))
        assertEquals(0, BigDecimal("492.76").compareTo(r.quotes.first { it.instrument.code == "MLC" }.value))
    }
}
