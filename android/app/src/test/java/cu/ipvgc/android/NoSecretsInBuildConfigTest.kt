package cu.ipvgc.android

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoSecretsInBuildConfigTest {
    @Test
    fun buildConfigHasOnlyPublicPlaceholders() {
        assertThat(BuildConfig.API_BASE_URL).doesNotContain("eltoque")
        assertThat(BuildConfig.LICENSE_PUBLIC_KEY_PEM).doesNotContain("PRIVATE")
    }
}
