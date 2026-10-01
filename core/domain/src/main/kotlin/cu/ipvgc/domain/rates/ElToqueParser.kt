package cu.ipvgc.domain.rates

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import cu.ipvgc.domain.hash.ContentHash
import cu.ipvgc.domain.money.InstrumentCode
import java.math.BigDecimal

/**
 * Analizador estricto y tolerante a claves nuevas (doc 10.3 / 13.3).
 * No redondea ni altera valores. `source_ts_utc` queda nulo hasta D-04.
 */
object ElToqueParser {
    val DEFAULT_MAP: Map<String, String> =
        mapOf("USD" to "USD", "ECU" to "EUR", "MLC" to "MLC")

    private val mapper =
        ObjectMapper().apply {
            enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            factory.configure(JsonParser.Feature.USE_BIG_DECIMAL_FOR_FLOATS, true)
        }

    fun parse(
        body: String,
        contentType: String? = "application/json",
        mappings: Map<String, String> = DEFAULT_MAP,
    ): FetchResult {
        val ct = contentType?.lowercase().orEmpty()
        if (ct.contains("html") || body.trimStart().startsWith("<")) {
            return FetchResult(ProviderOutcome.INVALID_PAYLOAD, error = "html_or_challenge")
        }
        val root: JsonNode =
            try {
                mapper.readTree(body)
            } catch (_: Exception) {
                return FetchResult(ProviderOutcome.INVALID_PAYLOAD, error = "malformed_json")
            }
        if (root == null || !root.isObject) {
            return FetchResult(ProviderOutcome.INVALID_PAYLOAD, error = "not_object")
        }
        val tasas = root.get("tasas")
        if (tasas == null || !tasas.isObject) {
            return FetchResult(ProviderOutcome.INVALID_PAYLOAD, error = "missing_tasas")
        }
        val quotes = mutableListOf<RateQuote>()
        val unknown = mutableListOf<String>()
        val it = tasas.fields()
        while (it.hasNext()) {
            val (code, node) = it.next()
            val instrument = mappings[code]
            if (instrument == null) {
                unknown += code
                continue
            }
            val value = readPositive(node) ?: return FetchResult(
                ProviderOutcome.INVALID_PAYLOAD,
                error = "invalid_value:$code",
                unknownKeys = unknown,
            )
            quotes += RateQuote(code, InstrumentCode.parse(instrument), value)
        }
        if (quotes.isEmpty()) {
            return FetchResult(ProviderOutcome.INVALID_PAYLOAD, error = "no_mapped_quotes", unknownKeys = unknown)
        }
        val rawTs =
            mapOf(
                "date" to root.path("date").takeUnless { it.isMissingNode || it.isNull }?.asText(),
                "hour" to root.path("hour").takeUnless { it.isMissingNode || it.isNull }?.asInt(),
                "minutes" to root.path("minutes").takeUnless { it.isMissingNode || it.isNull }?.asInt(),
                "seconds" to root.path("seconds").takeUnless { it.isMissingNode || it.isNull }?.asInt(),
            )
        return FetchResult(
            outcome = ProviderOutcome.OK,
            quotes = quotes,
            unknownKeys = unknown,
            sourceTsRaw = rawTs,
            payloadHash = ContentHash.sha256Hex(body),
        )
    }

    private fun readPositive(node: JsonNode): BigDecimal? {
        val n =
            when {
                node.isNumber -> node.decimalValue()
                node.isTextual -> {
                    val text = node.asText()
                    if (text.contains("NaN", ignoreCase = true) || text.contains("Inf", ignoreCase = true)) return null
                    try {
                        BigDecimal(text)
                    } catch (_: Exception) {
                        return null
                    }
                }
                else -> return null
            }
        if (n <= BigDecimal.ZERO) return null
        return n
    }
}
