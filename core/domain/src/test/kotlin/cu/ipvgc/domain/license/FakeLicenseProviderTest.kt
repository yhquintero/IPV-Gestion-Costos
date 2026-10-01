package cu.ipvgc.domain.license

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class FakeLicenseProviderTest {
    private fun provider(): FakeLicenseProvider {
        val p = FakeLicenseProvider(clock = { Instant.parse("2026-03-15T12:00:00Z") })
        PolicyCatalog.provision(p)
        return p
    }

    @Test
    fun `create is idempotent by key and does not expose a license key`() {
        val p = provider()
        val req =
            CreateLicenseRequest(
                policyCode = "IPV-MENSUAL",
                organizationId = "org",
                userId = "u",
                contractItemId = "ci",
                idempotencyKey = "issue-1",
            )
        val a = p.createLicense(req)
        val b = p.createLicense(req)
        assertEquals(a.id, b.id)
        assertEquals("INACTIVE", a.status)
    }

    @Test
    fun `first activation starts vigencia FROM_FIRST_ACTIVATION`() {
        val p = provider()
        val lic =
            p.createLicense(
                CreateLicenseRequest("IPV-MENSUAL", "org", "u", null, idempotencyKey = "k"),
            )
        p.activateMachine(lic.id, "fp-a", "ANDROID")
        val after = p.get(lic.id)!!
        assertEquals("ACTIVE", after.status)
        assertEquals(Instant.parse("2026-04-14T12:00:00Z"), after.expiresAt)
    }

    @Test
    fun `device limit is enforced`() {
        val p = provider()
        val lic =
            p.createLicense(
                CreateLicenseRequest("IPV-MENSUAL", "org", "u", null, maxMachines = 2, idempotencyKey = "k"),
            )
        p.activateMachine(lic.id, "fp-1", "ANDROID")
        p.activateMachine(lic.id, "fp-2", "WEB")
        assertThrows(IllegalStateException::class.java) {
            p.activateMachine(lic.id, "fp-3", "ANDROID")
        }
        assertEquals("TOO_MANY_MACHINES", runCatching { p.activateMachine(lic.id, "fp-3", "ANDROID") }.exceptionOrNull()?.message)
    }

    @Test
    fun `machine file hmac verifies`() {
        val p = provider()
        val lic =
            p.createLicense(CreateLicenseRequest("IPV-MENSUAL", "org", "u", null, idempotencyKey = "k"))
        p.activateMachine(lic.id, "fp-a", "ANDROID")
        val file = p.checkoutMachineFile(lic.id, "fp-a", Duration.ofDays(3))
        assertTrue(p.verifyMachineFile(file.pem))
        assertEquals("HMAC-FAKE", file.alg)
    }

    @Test
    fun `renewal FROM_EXPIRY is idempotent`() {
        val p = provider()
        val lic =
            p.createLicense(CreateLicenseRequest("IPV-MENSUAL", "org", "u", null, idempotencyKey = "k"))
        p.activateMachine(lic.id, "fp-a", "ANDROID")
        val r1 = p.renew(lic.id, "ren-1")
        val r2 = p.renew(lic.id, "ren-1")
        assertEquals(r1.expiresAt, r2.expiresAt)
        assertEquals(Instant.parse("2026-05-14T12:00:00Z"), r1.expiresAt)
    }

    @Test
    fun `revoked license cannot renew`() {
        val p = provider()
        val lic =
            p.createLicense(CreateLicenseRequest("IPV-MENSUAL", "org", "u", null, idempotencyKey = "k"))
        p.activateMachine(lic.id, "fp-a", "ANDROID")
        p.revoke(lic.id)
        assertThrows(IllegalStateException::class.java) { p.renew(lic.id, "ren") }
    }

    @Test
    fun `cloud adapter refuses until D-05`() {
        val cloud = KeygenCloudDisabledProvider()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                cloud.upsertPolicy(PolicyCatalog.IPV_MENSUAL)
            }
        assertTrue(ex.message!!.contains("D-05"))
    }

    @Test
    fun `webhook event id is idempotent`() {
        val p = provider()
        assertTrue(p.recordWebhook("evt-1", "license.updated"))
        assertFalse(p.recordWebhook("evt-1", "license.updated"))
    }

    @Test
    fun `policies provisioned once stay stable`() {
        val p = provider()
        val second = PolicyCatalog.provision(p)
        assertEquals(PolicyCatalog.ALL.map { it.code }, second)
        assertNotEquals(0, second.size)
    }
}
