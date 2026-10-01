package cu.ipvgc.server.access

import java.util.UUID

data class CurrentUser(
    val userId: UUID,
    val organizationId: UUID,
    val email: String,
    val displayName: String,
    val roles: Set<String>,
    val permissions: Set<String>,
) {
    fun require(permission: String) {
        if (permission !in permissions) {
            throw cu.ipvgc.server.web.ApiException.forbidden(
                "forbidden",
                "missing permission $permission",
            )
        }
    }

    fun has(permission: String): Boolean = permission in permissions
}
