package cu.ipvgc.domain.money

/**
 * Código de instrumento o moneda (ISO-4217 cuando aplica: CUP, USD, EUR;
 * etiquetas de elTOQUE cuando no: MLC, CLA, ZELLE).
 *
 * No se interpreta el significado económico de MLC/ZELLE/CLA (D-27).
 */
@JvmInline
value class InstrumentCode(val code: String) {
    init {
        require(CODE.matches(code)) {
            "Instrument code must be 3–8 uppercase letters, got '$code'"
        }
    }

    override fun toString(): String = code

    companion object {
        private val CODE = Regex("^[A-Z]{3,8}$")

        fun parse(raw: String): InstrumentCode = InstrumentCode(raw.trim().uppercase())
    }
}
