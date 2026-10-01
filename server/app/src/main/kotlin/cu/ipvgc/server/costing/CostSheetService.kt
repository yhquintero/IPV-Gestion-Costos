package cu.ipvgc.server.costing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cu.ipvgc.domain.costing.CostSheetStatus
import cu.ipvgc.domain.costing.CostSheetTransitions
import cu.ipvgc.domain.hash.ContentHash
import cu.ipvgc.domain.money.CostingArithmetic
import cu.ipvgc.domain.money.InstrumentCode
import cu.ipvgc.domain.money.Money
import cu.ipvgc.domain.rules.RuleLine
import cu.ipvgc.domain.rules.RuleSheet
import cu.ipvgc.domain.rules.StructuralRuleEngine
import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.notifications.NotificationService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CreateSheetRequest(val company_id: UUID, val product_id: UUID, val branch_id: UUID?, val code: String)
data class LineRequest(
    val line_type: String = "MATERIAL",
    val raw_material_id: UUID? = null,
    val ipv_value_id: UUID? = null,
    val quantity: BigDecimal,
    val unit_cost_snapshot: BigDecimal? = null,
    val unit_currency: String? = null,
)
data class PatchDraftRequest(val yield_qty: BigDecimal? = null, val calc_currency: String? = null, val lines: List<LineRequest>? = null)
data class CommentRequest(val comment: String? = null, val reason: String? = null, val valid_from: LocalDate? = null)

@Service
class CostSheetService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
    private val notifications: NotificationService,
) {
    private val mapper = jacksonObjectMapper()

    fun list(): List<Map<String, Any?>> {
        currentUser().require("costs:view")
        return jdbc.query(
            """
            SELECT s.id, s.code, s.product_id, s.company_id,
                   v.id AS version_id, v.version_no, v.status, v.version, v.total_cost, v.unit_cost
              FROM cost_sheets s
              LEFT JOIN LATERAL (
                    SELECT * FROM cost_sheet_versions
                     WHERE cost_sheet_id = s.id AND deleted_at IS NULL
                     ORDER BY version_no DESC LIMIT 1
              ) v ON true
             WHERE s.deleted_at IS NULL
             ORDER BY s.code
            """.trimIndent(),
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id"),
                "code" to rs.getString("code"),
                "product_id" to rs.getObject("product_id"),
                "company_id" to rs.getObject("company_id"),
                "current_version" to mapOf(
                    "id" to rs.getObject("version_id"),
                    "version_no" to rs.getInt("version_no"),
                    "status" to rs.getString("status"),
                    "etag" to rs.getLong("version"),
                    "total_cost" to rs.getBigDecimal("total_cost"),
                    "unit_cost" to rs.getBigDecimal("unit_cost"),
                ),
            )
        }
    }

    fun getVersion(sheetId: UUID, versionId: UUID): Map<String, Any?> {
        currentUser().require("costs:view")
        val header = loadVersion(sheetId, versionId) ?: throw ApiException.notFound("cost_sheet_version")
        val lines = jdbc.query(
            """
            SELECT id, line_no, line_type, raw_material_id, ipv_value_id, quantity,
                   unit_cost_snapshot, unit_currency, line_cost
              FROM cost_sheet_lines WHERE version_id = ? ORDER BY line_no
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "line_no" to rs.getInt("line_no"),
                    "line_type" to rs.getString("line_type"),
                    "raw_material_id" to rs.getObject("raw_material_id"),
                    "ipv_value_id" to rs.getObject("ipv_value_id"),
                    "quantity" to rs.getBigDecimal("quantity"),
                    "unit_cost_snapshot" to rs.getBigDecimal("unit_cost_snapshot"),
                    "unit_currency" to rs.getString("unit_currency"),
                    "line_cost" to rs.getBigDecimal("line_cost"),
                )
            },
            versionId,
        )
        return header + mapOf("lines" to lines)
    }

    @Transactional
    fun create(req: CreateSheetRequest): Map<String, Any?> {
        currentUser().require("costing:edit")
        val sheetId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO cost_sheets (id, organization_id, company_id, branch_id, product_id, code, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            sheetId, currentUser().organizationId, req.company_id, req.branch_id, req.product_id, req.code, currentUser().userId,
        )
        jdbc.update(
            """
            INSERT INTO cost_sheet_versions (
                id, organization_id, cost_sheet_id, version_no, status, calc_currency, created_by
            ) VALUES (?, ?, ?, 1, 'BORRADOR', 'CUP', ?)
            """.trimIndent(),
            versionId, currentUser().organizationId, sheetId, currentUser().userId,
        )
        audit.record("COSTING.VERSION.CREATE", "cost_sheet_versions", versionId)
        return mapOf("id" to sheetId, "version_id" to versionId, "status" to "BORRADOR")
    }

    @Transactional
    fun patchDraft(sheetId: UUID, versionId: UUID, req: PatchDraftRequest, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:edit")
        val v = lock(sheetId, versionId, ifMatch)
        if (v.status != CostSheetStatus.BORRADOR) {
            throw ApiException.conflict("frozen", "only BORRADOR can be edited")
        }
        if (req.yield_qty != null || req.calc_currency != null) {
            jdbc.update(
                "UPDATE cost_sheet_versions SET yield_qty = COALESCE(?, yield_qty), calc_currency = COALESCE(?, calc_currency) WHERE id = ?",
                req.yield_qty, req.calc_currency, versionId,
            )
        }
        if (req.lines != null) {
            jdbc.update("DELETE FROM cost_sheet_lines WHERE version_id = ?", versionId)
            req.lines.forEachIndexed { idx, line -> replaceLine(versionId, idx + 1, line) }
            recalculate(versionId)
        }
        audit.record("COSTING.VERSION.EDIT", "cost_sheet_versions", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun submit(sheetId: UUID, versionId: UUID, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:submit")
        val v = lock(sheetId, versionId, ifMatch)
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.EN_REVISION)
        val findings = evaluate(versionId, v.companyId)
        val blocking = StructuralRuleEngine.blocking(findings)
        if (blocking.isNotEmpty()) {
            throw ApiException.unprocessable("rules_failed", "blocking rules failed", mapOf("findings" to findings))
        }
        val snapshotId = freezeRates()
        recalculate(versionId)
        val hash = contentHash(versionId)
        jdbc.update(
            """
            UPDATE cost_sheet_versions
               SET status = 'EN_REVISION',
                   rate_snapshot_id = ?,
                   content_hash = decode(?, 'hex'),
                   submitted_at = clock_timestamp(),
                   submitted_by = ?
             WHERE id = ?
            """.trimIndent(),
            snapshotId, hash, currentUser().userId, versionId,
        )
        insertHistory(versionId, v.status.name, "EN_REVISION", null)
        audit.record("COSTING.VERSION.SUBMIT", "cost_sheet_versions", versionId)
        notifications.notifyRole("REVISOR", "COST_SHEET_SUBMITTED", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun validate(sheetId: UUID, versionId: UUID, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:validate")
        val v = lock(sheetId, versionId, ifMatch)
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.VALIDADA)
        fourEyes(v.createdBy)
        assertHash(versionId)
        val findings = evaluate(versionId, v.companyId)
        if (StructuralRuleEngine.blocking(findings).isNotEmpty()) {
            throw ApiException.unprocessable("rules_failed", "blocking rules failed", mapOf("findings" to findings))
        }
        jdbc.update(
            "UPDATE cost_sheet_versions SET status = 'VALIDADA', validated_at = clock_timestamp(), validated_by = ? WHERE id = ?",
            currentUser().userId, versionId,
        )
        insertHistory(versionId, v.status.name, "VALIDADA", null)
        audit.record("COSTING.VERSION.VALIDATE", "cost_sheet_versions", versionId)
        notifications.notifyRole("APROBADOR", "COST_SHEET_VALIDATED", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun approve(sheetId: UUID, versionId: UUID, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:approve")
        val v = lock(sheetId, versionId, ifMatch)
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.APROBADA)
        fourEyes(v.createdBy)
        assertHash(versionId)
        jdbc.update(
            "UPDATE cost_sheet_versions SET status = 'APROBADA', approved_at = clock_timestamp(), approved_by = ? WHERE id = ?",
            currentUser().userId, versionId,
        )
        insertHistory(versionId, v.status.name, "APROBADA", null)
        audit.record("COSTING.VERSION.APPROVE", "cost_sheet_versions", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun activate(sheetId: UUID, versionId: UUID, validFrom: LocalDate, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:activate")
        val v = lock(sheetId, versionId, ifMatch)
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.VIGENTE)
        jdbc.update(
            """
            UPDATE cost_sheet_versions
               SET status = 'REEMPLAZADA', valid_to = ?
             WHERE cost_sheet_id = ? AND status = 'VIGENTE' AND deleted_at IS NULL AND id <> ?
            """.trimIndent(),
            validFrom, sheetId, versionId,
        )
        jdbc.update(
            """
            UPDATE cost_sheet_versions
               SET status = 'VIGENTE', valid_from = ?, activated_at = clock_timestamp(), activated_by = ?
             WHERE id = ?
            """.trimIndent(),
            validFrom, currentUser().userId, versionId,
        )
        insertHistory(versionId, v.status.name, "VIGENTE", null)
        audit.record("COSTING.VERSION.ACTIVATE", "cost_sheet_versions", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun returnToDraft(sheetId: UUID, versionId: UUID, comment: String, ifMatch: Long?): Map<String, Any?> {
        val v = lock(sheetId, versionId, ifMatch)
        val perm = if (v.status == CostSheetStatus.EN_REVISION) "costing:validate" else "costing:approve"
        currentUser().require(perm)
        if (comment.isBlank()) throw ApiException.badRequest("comment_required", "comment is required")
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.BORRADOR)
        jdbc.update(
            "UPDATE cost_sheet_versions SET status = 'BORRADOR', rate_snapshot_id = NULL, content_hash = NULL WHERE id = ?",
            versionId,
        )
        insertHistory(versionId, v.status.name, "BORRADOR", comment)
        audit.record("COSTING.VERSION.RETURN", "cost_sheet_versions", versionId, reason = comment)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun annul(sheetId: UUID, versionId: UUID, reason: String, ifMatch: Long?): Map<String, Any?> {
        currentUser().require("costing:annul")
        if (reason.isBlank()) throw ApiException.badRequest("reason_required", "reason is required")
        val v = lock(sheetId, versionId, ifMatch)
        CostSheetTransitions.requireTransition(v.status, CostSheetStatus.ANULADA)
        jdbc.update(
            """
            UPDATE cost_sheet_versions
               SET status = 'ANULADA', annul_reason = ?, annulled_at = clock_timestamp(), annulled_by = ?
             WHERE id = ?
            """.trimIndent(),
            reason, currentUser().userId, versionId,
        )
        jdbc.update(
            "UPDATE ipv_control_lines SET version_annulled = true WHERE cost_sheet_version_id = ?",
            versionId,
        )
        insertHistory(versionId, v.status.name, "ANULADA", reason)
        audit.record("COSTING.VERSION.ANNUL", "cost_sheet_versions", versionId, reason = reason)
        notifications.notifyRole("CONTROLADOR_IPV", "COST_SHEET_ANNULLED", versionId)
        return getVersion(sheetId, versionId)
    }

    @Transactional
    fun newVersion(sheetId: UUID, fromVersionId: UUID): Map<String, Any?> {
        currentUser().require("costing:edit")
        val from = loadVersion(sheetId, fromVersionId) ?: throw ApiException.notFound("cost_sheet_version")
        val nextNo = jdbc.queryForObject(
            "SELECT COALESCE(max(version_no), 0) + 1 FROM cost_sheet_versions WHERE cost_sheet_id = ?",
            Int::class.java,
            sheetId,
        )!!
        val versionId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO cost_sheet_versions (
                id, organization_id, cost_sheet_id, version_no, status, parent_version_id,
                yield_qty, calc_currency, created_by
            ) VALUES (?, ?, ?, ?, 'BORRADOR', ?, ?, 'CUP', ?)
            """.trimIndent(),
            versionId, currentUser().organizationId, sheetId, nextNo, fromVersionId,
            from.yieldQty, currentUser().userId,
        )
        jdbc.update(
            """
            INSERT INTO cost_sheet_lines (
                organization_id, version_id, line_no, line_type, raw_material_id, ipv_value_id,
                quantity, waste_pct, unit_cost_snapshot, unit_currency, rate_to_calc, line_cost
            )
            SELECT organization_id, ?, line_no, line_type, raw_material_id, ipv_value_id,
                   quantity, waste_pct, unit_cost_snapshot, unit_currency, rate_to_calc, line_cost
              FROM cost_sheet_lines WHERE version_id = ?
            """.trimIndent(),
            versionId, fromVersionId,
        )
        audit.record("COSTING.VERSION.CREATE", "cost_sheet_versions", versionId)
        return mapOf("id" to sheetId, "version_id" to versionId, "status" to "BORRADOR", "from_version_id" to fromVersionId)
    }

    fun prevalidate(sheetId: UUID, versionId: UUID): Map<String, Any?> {
        currentUser().require("costs:view")
        val v = loadVersion(sheetId, versionId) ?: throw ApiException.notFound("cost_sheet_version")
        val findings = evaluate(versionId, v.companyId)
        return mapOf("findings" to findings, "blocking" to StructuralRuleEngine.blocking(findings).isNotEmpty())
    }

    private fun replaceLine(versionId: UUID, lineNo: Int, line: LineRequest) {
        val snapshot = line.unit_cost_snapshot ?: line.ipv_value_id?.let { ipvId ->
            jdbc.queryForObject("SELECT unit_price FROM ipv_values WHERE id = ?", BigDecimal::class.java, ipvId)
        } ?: throw ApiException.badRequest("unit_cost", "unit_cost_snapshot or ipv_value_id required")
        val ccy = line.unit_currency ?: "CUP"
        val lineCost = CostingArithmetic.lineSubtotal(line.quantity, Money.of(snapshot, InstrumentCode.parse(ccy))).amount
        jdbc.update(
            """
            INSERT INTO cost_sheet_lines (
                organization_id, version_id, line_no, line_type, raw_material_id, ipv_value_id,
                quantity, unit_cost_snapshot, unit_currency, rate_to_calc, line_cost
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
            """.trimIndent(),
            currentUser().organizationId, versionId, lineNo, line.line_type, line.raw_material_id,
            line.ipv_value_id, line.quantity, snapshot, ccy, lineCost,
        )
    }

    private fun recalculate(versionId: UUID) {
        val currency = jdbc.queryForObject(
            "SELECT COALESCE(calc_currency, 'CUP') FROM cost_sheet_versions WHERE id = ?",
            String::class.java,
            versionId,
        )!!
        val lines = jdbc.query(
            "SELECT line_cost FROM cost_sheet_lines WHERE version_id = ? ORDER BY line_no",
            { rs, _ -> Money.of(rs.getBigDecimal(1), InstrumentCode.parse(currency)) },
            versionId,
        )
        val total = CostingArithmetic.totalOrZero(lines, InstrumentCode.parse(currency))
        val yield = jdbc.query(
            "SELECT yield_qty FROM cost_sheet_versions WHERE id = ?",
            { rs, _ -> rs.getBigDecimal(1) },
            versionId,
        ).firstOrNull()
        val unit = if (yield != null && yield > BigDecimal.ZERO) {
            CostingArithmetic.unitCostFromYield(total, yield).amount
        } else {
            null
        }
        jdbc.update(
            "UPDATE cost_sheet_versions SET total_cost = ?, unit_cost = ? WHERE id = ?",
            total.amount, unit, versionId,
        )
    }

    private fun evaluate(versionId: UUID, companyId: UUID) : List<cu.ipvgc.domain.rules.RuleFinding> {
        val yield = jdbc.query(
            "SELECT yield_qty FROM cost_sheet_versions WHERE id = ?",
            { rs, _ -> rs.getBigDecimal(1) },
            versionId,
        ).firstOrNull()
        val allowed = jdbc.query(
            "SELECT currency FROM company_allowed_currencies WHERE company_id = ?",
            { rs, _ -> rs.getString(1) },
            companyId,
        ).toSet()
        val requireIpv = jdbc.queryForObject(
            "SELECT COALESCE((settings->>'lines_require_ipv_value')::boolean, true) FROM companies WHERE id = ?",
            Boolean::class.java,
            companyId,
        ) ?: true
        val lines = jdbc.query(
            """
            SELECT l.line_no, l.line_type, l.quantity, l.ipv_value_id, l.unit_currency,
                   v.valid_from, v.valid_to
              FROM cost_sheet_lines l
              LEFT JOIN ipv_values v ON v.id = l.ipv_value_id
             WHERE l.version_id = ?
             ORDER BY l.line_no
            """.trimIndent(),
            { rs, _ ->
                RuleLine(
                    lineNo = rs.getInt("line_no"),
                    lineType = rs.getString("line_type"),
                    quantity = rs.getBigDecimal("quantity"),
                    ipvValueId = rs.getString("ipv_value_id"),
                    ipvValidFrom = rs.getDate("valid_from")?.toLocalDate(),
                    ipvValidTo = rs.getDate("valid_to")?.toLocalDate(),
                    unitCurrency = rs.getString("unit_currency"),
                )
            },
            versionId,
        )
        return StructuralRuleEngine.evaluate(
            RuleSheet(
                yieldQty = yield,
                asOf = LocalDate.now(),
                allowedCurrencies = allowed,
                linesRequireIpvValue = requireIpv,
                lines = lines,
            ),
        )
    }

    private fun freezeRates(): UUID {
        val items = jdbc.query(
            """
            SELECT c.instrument_code, s.value, s.id
              FROM exchange_rate_current c
              JOIN exchange_rate_samples s ON s.id = c.sample_id
            """.trimIndent(),
        ) { rs, _ -> Triple(rs.getString(1), rs.getBigDecimal(2), rs.getObject(3, UUID::class.java)) }
        val canonical = items.sortedBy { it.first }.joinToString("|") { "${it.first}:${it.second}" }
        val hash = ContentHash.sha256Hex(canonical)
        val existing = jdbc.query(
            "SELECT id FROM rate_snapshots WHERE encode(content_hash,'hex') = ?",
            { rs, _ -> rs.getObject(1, UUID::class.java) },
            hash,
        ).firstOrNull()
        if (existing != null) return existing
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO rate_snapshots (id, content_hash, status_at_capture, is_test) VALUES (?, decode(?, 'hex'), 'TEST', true)",
            id, hash,
        )
        items.forEach { (code, value, sampleId) ->
            jdbc.update(
                "INSERT INTO rate_snapshot_items (snapshot_id, instrument_code, sample_id, value) VALUES (?, ?, ?, ?)",
                id, code, sampleId, value,
            )
        }
        return id
    }

    private fun contentHash(versionId: UUID): String {
        val yield = jdbc.query(
            "SELECT yield_qty FROM cost_sheet_versions WHERE id = ?",
            { rs, _ -> rs.getBigDecimal(1) },
            versionId,
        ).firstOrNull()
        val lines = jdbc.query(
            """
            SELECT line_no, line_type, raw_material_id, ipv_value_id, quantity,
                   unit_cost_snapshot, unit_currency, line_cost
              FROM cost_sheet_lines WHERE version_id = ? ORDER BY line_no
            """.trimIndent(),
            { rs, _ ->
                linkedMapOf(
                    "line_no" to rs.getInt("line_no"),
                    "line_type" to rs.getString("line_type"),
                    "raw_material_id" to rs.getString("raw_material_id"),
                    "ipv_value_id" to rs.getString("ipv_value_id"),
                    "quantity" to rs.getBigDecimal("quantity").stripTrailingZeros().toPlainString(),
                    "unit_cost_snapshot" to rs.getBigDecimal("unit_cost_snapshot")?.stripTrailingZeros()?.toPlainString(),
                    "unit_currency" to rs.getString("unit_currency"),
                    "line_cost" to rs.getBigDecimal("line_cost")?.stripTrailingZeros()?.toPlainString(),
                )
            },
            versionId,
        )
        val payload = linkedMapOf("yield_qty" to yield?.stripTrailingZeros()?.toPlainString(), "lines" to lines)
        return ContentHash.sha256Hex(mapper.writeValueAsString(payload))
    }

    private fun assertHash(versionId: UUID) {
        val stored = jdbc.query(
            "SELECT encode(content_hash,'hex') FROM cost_sheet_versions WHERE id = ?",
            { rs, _ -> rs.getString(1) },
            versionId,
        ).firstOrNull()
        val current = contentHash(versionId)
        if (stored != null && stored != current) {
            throw ApiException.conflict("content_hash_mismatch", "sheet content was altered outside the flow")
        }
    }

    private fun fourEyes(createdBy: UUID?) {
        if (createdBy != null && createdBy == currentUser().userId) {
            throw ApiException.forbidden("four_eyes", "author cannot validate or approve their own sheet")
        }
    }

    private fun insertHistory(versionId: UUID, from: String, to: String, reason: String?) {
        jdbc.update(
            """
            INSERT INTO status_history (organization_id, entity_type, entity_id, from_status, to_status, actor_id, reason)
            VALUES (?, 'cost_sheet_version', ?, ?, ?, ?, ?)
            """.trimIndent(),
            currentUser().organizationId, versionId, from, to, currentUser().userId, reason,
        )
    }

    private fun lock(sheetId: UUID, versionId: UUID, ifMatch: Long?): VersionRow {
        val row = jdbc.query(
            """
            SELECT v.id, v.status, v.version, v.created_by, v.yield_qty, s.company_id
              FROM cost_sheet_versions v
              JOIN cost_sheets s ON s.id = v.cost_sheet_id
             WHERE v.id = ? AND v.cost_sheet_id = ?
             FOR UPDATE OF v
            """.trimIndent(),
            { rs, _ ->
                VersionRow(
                    id = rs.getObject("id", UUID::class.java),
                    status = CostSheetStatus.valueOf(rs.getString("status")),
                    version = rs.getLong("version"),
                    createdBy = rs.getObject("created_by") as UUID?,
                    yieldQty = rs.getBigDecimal("yield_qty"),
                    companyId = rs.getObject("company_id", UUID::class.java),
                )
            },
            versionId,
            sheetId,
        ).firstOrNull() ?: throw ApiException.notFound("cost_sheet_version")
        if (ifMatch != null && ifMatch != row.version) {
            throw ApiException.conflict("version_conflict", "If-Match does not match current version")
        }
        return row
    }

    private fun loadVersion(sheetId: UUID, versionId: UUID): VersionRow? = jdbc.query(
        """
        SELECT v.id, v.status, v.version, v.created_by, v.yield_qty, s.company_id,
               v.total_cost, v.unit_cost, v.valid_from, v.valid_to, encode(v.content_hash,'hex') AS ch
          FROM cost_sheet_versions v
          JOIN cost_sheets s ON s.id = v.cost_sheet_id
         WHERE v.id = ? AND v.cost_sheet_id = ?
        """.trimIndent(),
        { rs, _ ->
            VersionRow(
                id = rs.getObject("id", UUID::class.java),
                status = CostSheetStatus.valueOf(rs.getString("status")),
                version = rs.getLong("version"),
                createdBy = rs.getObject("created_by") as UUID?,
                yieldQty = rs.getBigDecimal("yield_qty"),
                companyId = rs.getObject("company_id", UUID::class.java),
                extra = mapOf(
                    "total_cost" to rs.getBigDecimal("total_cost"),
                    "unit_cost" to rs.getBigDecimal("unit_cost"),
                    "valid_from" to rs.getDate("valid_from")?.toLocalDate(),
                    "valid_to" to rs.getDate("valid_to")?.toLocalDate(),
                    "content_hash" to rs.getString("ch"),
                    "etag" to rs.getLong("version"),
                    "status" to rs.getString("status"),
                    "id" to rs.getObject("id"),
                    "cost_sheet_id" to sheetId,
                ),
            )
        },
        versionId,
        sheetId,
    ).firstOrNull()

    private data class VersionRow(
        val id: UUID,
        val status: CostSheetStatus,
        val version: Long,
        val createdBy: UUID?,
        val yieldQty: BigDecimal?,
        val companyId: UUID,
        val extra: Map<String, Any?> = emptyMap(),
    ) : Map<String, Any?> by extra
}
