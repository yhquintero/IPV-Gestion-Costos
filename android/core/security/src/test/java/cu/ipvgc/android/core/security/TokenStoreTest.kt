package cu.ipvgc.android.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenStoreTest {
    @Test
    fun roundTripAndClear() {
        val store = InMemoryTokenStore()
        store.save("access-1", "refresh-1")
        assertThat(store.accessToken()).isEqualTo("access-1")
        store.clear()
        assertThat(store.accessToken()).isNull()
        assertThat(store.refreshToken()).isNull()
    }
}
