package cu.ipvgc.domain.ipvcontrol

enum class IpvControlStatus {
    PENDIENTE,
    EN_PROCESO,
    VALIDADO,
    CON_DIFERENCIAS,
}

enum class IpvControlMode {
    CONSISTENCIA,
    DERIVA_COSTOS,
    CONSUMO,
}

object IpvControlTransitions {
    private val allowed: Set<Pair<IpvControlStatus, IpvControlStatus>> = setOf(
        IpvControlStatus.PENDIENTE to IpvControlStatus.EN_PROCESO,
        IpvControlStatus.EN_PROCESO to IpvControlStatus.VALIDADO,
        IpvControlStatus.EN_PROCESO to IpvControlStatus.CON_DIFERENCIAS,
    )

    fun canTransition(from: IpvControlStatus, to: IpvControlStatus): Boolean =
        (from to to) in allowed

    fun all(): Set<Pair<IpvControlStatus, IpvControlStatus>> = allowed
}
