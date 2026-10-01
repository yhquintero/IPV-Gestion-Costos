package cu.ipvgc.domain.costing

/**
 * Ciclo de vida de una versión de Ficha de Costo (doc 6.2).
 * VENCIDA no es estado: es una condición derivada de `valid_to`.
 */
enum class CostSheetStatus {
    BORRADOR,
    EN_REVISION,
    VALIDADA,
    APROBADA,
    VIGENTE,
    REEMPLAZADA,
    ANULADA,
    ;

    val isOpenDraft: Boolean get() = this == BORRADOR
    val isFrozen: Boolean get() = this != BORRADOR
    val allowsControlLine: Boolean get() = this == VIGENTE || this == REEMPLAZADA
}

object CostSheetTransitions {
    private val allowed: Set<Pair<CostSheetStatus, CostSheetStatus>> = setOf(
        CostSheetStatus.BORRADOR to CostSheetStatus.EN_REVISION,
        CostSheetStatus.EN_REVISION to CostSheetStatus.BORRADOR,
        CostSheetStatus.EN_REVISION to CostSheetStatus.VALIDADA,
        CostSheetStatus.EN_REVISION to CostSheetStatus.ANULADA,
        CostSheetStatus.VALIDADA to CostSheetStatus.BORRADOR,
        CostSheetStatus.VALIDADA to CostSheetStatus.APROBADA,
        CostSheetStatus.VALIDADA to CostSheetStatus.ANULADA,
        CostSheetStatus.APROBADA to CostSheetStatus.VIGENTE,
        CostSheetStatus.APROBADA to CostSheetStatus.ANULADA,
        CostSheetStatus.VIGENTE to CostSheetStatus.REEMPLAZADA,
        CostSheetStatus.VIGENTE to CostSheetStatus.ANULADA,
    )

    fun canTransition(from: CostSheetStatus, to: CostSheetStatus): Boolean =
        (from to to) in allowed

    fun requireTransition(from: CostSheetStatus, to: CostSheetStatus) {
        require(canTransition(from, to)) {
            "I-06 illegal cost-sheet transition $from -> $to"
        }
    }

    fun all(): Set<Pair<CostSheetStatus, CostSheetStatus>> = allowed
}
