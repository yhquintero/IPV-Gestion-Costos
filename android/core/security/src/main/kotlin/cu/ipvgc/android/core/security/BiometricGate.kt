package cu.ipvgc.android.core.security

interface BiometricGate {
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
}

class InMemoryBiometricGate : BiometricGate {
    @Volatile private var enabled: Boolean = false

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(value: Boolean) {
        enabled = value
    }
}
