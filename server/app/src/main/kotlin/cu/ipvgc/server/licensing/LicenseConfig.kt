package cu.ipvgc.server.licensing

import cu.ipvgc.domain.license.FakeLicenseProvider
import cu.ipvgc.domain.license.KeygenCloudDisabledProvider
import cu.ipvgc.domain.license.LicenseProvider
import cu.ipvgc.domain.license.PolicyCatalog
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class LicenseConfig {
    @Bean
    fun licenseProvider(
        @Value("\${ipvgc.keygen.mode:FAKE}") mode: String,
    ): LicenseProvider {
        return when (mode.uppercase()) {
            "FAKE" ->
                FakeLicenseProvider(clock = { Instant.now() }).also { PolicyCatalog.provision(it) }
            else -> KeygenCloudDisabledProvider()
        }
    }
}
