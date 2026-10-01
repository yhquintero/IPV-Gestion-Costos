package cu.ipvgc.android.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutcomeTest {
    @Test
    fun mapTransformsOk() {
        val out: Outcome<Int> = Outcome.Ok(2).map { it * 3 }
        assertThat(out).isEqualTo(Outcome.Ok(6))
    }

    @Test
    fun mapKeepsErr() {
        val err: Outcome<Int> = Outcome.Err("x")
        assertThat(err.map { it + 1 }).isEqualTo(err)
    }
}
