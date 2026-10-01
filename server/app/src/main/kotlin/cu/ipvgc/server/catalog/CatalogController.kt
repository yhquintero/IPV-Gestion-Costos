package cu.ipvgc.server.catalog

import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CategoryBody(val company_id: UUID, val code: String, val name: String, val kind: String)
data class ProductBody(val company_id: UUID, val category_id: UUID, val kind: String, val code: String, val name: String)
data class MaterialBody(
    val company_id: UUID,
    val category_id: UUID,
    val base_unit_id: UUID,
    val code: String,
    val name: String,
)

@RestController
@RequestMapping("/api/v1")
class CatalogController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping("/categories")
    fun categories(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, company_id, code, name, kind FROM categories WHERE deleted_at IS NULL ORDER BY code",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id"),
            "company_id" to rs.getObject("company_id"),
            "code" to rs.getString("code"),
            "name" to rs.getString("name"),
            "kind" to rs.getString("kind"),
        )
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@RequestBody body: CategoryBody): Map<String, Any?> {
        currentUser().require("catalog:edit")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO categories (id, organization_id, company_id, code, name, kind, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, currentUser().organizationId, body.company_id, body.code, body.name, body.kind, currentUser().userId,
        )
        audit.record("CATALOG.CATEGORY.CREATE", "categories", id, after = body)
        return mapOf("id" to id)
    }

    @GetMapping("/raw-materials")
    fun materials(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, company_id, category_id, code, name, active FROM raw_materials WHERE deleted_at IS NULL ORDER BY code",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id"),
            "company_id" to rs.getObject("company_id"),
            "category_id" to rs.getObject("category_id"),
            "code" to rs.getString("code"),
            "name" to rs.getString("name"),
            "active" to rs.getBoolean("active"),
        )
    }

    @PostMapping("/raw-materials")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMaterial(@RequestBody body: MaterialBody): Map<String, Any?> {
        currentUser().require("catalog:edit")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO raw_materials (id, organization_id, company_id, category_id, base_unit_id, code, name, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, currentUser().organizationId, body.company_id, body.category_id, body.base_unit_id,
            body.code, body.name, currentUser().userId,
        )
        audit.record("CATALOG.MATERIAL.CREATE", "raw_materials", id)
        return mapOf("id" to id)
    }

    @GetMapping("/products")
    fun products(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, company_id, category_id, kind, code, name FROM products WHERE deleted_at IS NULL ORDER BY code",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id"),
            "company_id" to rs.getObject("company_id"),
            "category_id" to rs.getObject("category_id"),
            "kind" to rs.getString("kind"),
            "code" to rs.getString("code"),
            "name" to rs.getString("name"),
        )
    }

    @GetMapping("/products/{id}")
    fun product(@PathVariable id: UUID): Map<String, Any?> {
        return jdbc.query(
            "SELECT id, company_id, category_id, kind, code, name FROM products WHERE id = ? AND deleted_at IS NULL",
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "company_id" to rs.getObject("company_id"),
                    "category_id" to rs.getObject("category_id"),
                    "kind" to rs.getString("kind"),
                    "code" to rs.getString("code"),
                    "name" to rs.getString("name"),
                )
            },
            id,
        ).firstOrNull() ?: throw ApiException.notFound("product")
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@RequestBody body: ProductBody): Map<String, Any?> {
        currentUser().require("catalog:edit")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO products (id, organization_id, company_id, category_id, kind, code, name, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, currentUser().organizationId, body.company_id, body.category_id, body.kind,
            body.code, body.name, currentUser().userId,
        )
        audit.record("CATALOG.PRODUCT.CREATE", "products", id)
        return mapOf("id" to id)
    }

    @GetMapping("/units")
    fun units(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, code, name, dimension, to_base_factor FROM units ORDER BY code",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id"),
            "code" to rs.getString("code"),
            "name" to rs.getString("name"),
            "dimension" to rs.getString("dimension"),
            "to_base_factor" to rs.getBigDecimal("to_base_factor"),
        )
    }
}
