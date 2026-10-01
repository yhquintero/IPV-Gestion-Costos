package cu.ipvgc.server.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import cu.ipvgc.server.web.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID

data class AccessClaims(
    val userId: UUID,
    val organizationId: UUID,
    val email: String,
    val tokenId: String,
)

@Service
class JwtService(
    @Value("\${ipvgc.env:dev}") private val env: String,
    @Value("\${ipvgc.jwt.access-ttl-seconds:900}") private val ttlSeconds: Long,
) {
    private val jwk: OctetKeyPair = OctetKeyPairGenerator(Curve.Ed25519)
        .keyID(if (env == "prod") error("ipvgc.jwt.private-pem is required in production") else "dev-ed25519")
        .generate()
    private val signer = Ed25519Signer(jwk)
    private val verifier = Ed25519Verifier(jwk.toPublicJWK())

    val accessTtlSeconds: Long get() = ttlSeconds

    fun issueAccess(userId: UUID, organizationId: UUID, email: String): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .jwtID(UUID.randomUUID().toString())
            .claim("org", organizationId.toString())
            .claim("email", email)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(jwk.keyID).build(),
            claims,
        )
        jwt.sign(signer)
        return jwt.serialize()
    }

    fun parse(token: String): AccessClaims {
        try {
            val jwt = SignedJWT.parse(token)
            if (!jwt.verify(verifier)) throw ApiException.unauthorized()
            val claims = jwt.jwtClaimsSet
            if (claims.expirationTime.toInstant().isBefore(Instant.now())) {
                throw ApiException.unauthorized()
            }
            return AccessClaims(
                userId = UUID.fromString(claims.subject),
                organizationId = UUID.fromString(claims.getStringClaim("org")),
                email = claims.getStringClaim("email") ?: "",
                tokenId = claims.jwtid,
            )
        } catch (ex: ApiException) {
            throw ex
        } catch (_: Exception) {
            throw ApiException.unauthorized()
        }
    }
}
