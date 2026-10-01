package cu.ipvgc.server.db

import cu.ipvgc.seed.SeedIds
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InvariantsIT : PostgresIT() {

    @Test
    fun `I-01 app_rw cannot see or write another organization`() {
        asApp(alpha).use { connection ->
            connection.scalarLong("SELECT count(*) FROM organizations") shouldBe 1
            connection.scalarLong("SELECT count(*) FROM companies") shouldBe 1
            connection.scalarLong("SELECT count(*) FROM products WHERE code = 'SECRET'") shouldBe 0
            connection.scalarLong("SELECT count(*) FROM cost_sheets") shouldBe 2
            expectFailure(
                """
                INSERT INTO companies (id, organization_id, name, base_currency)
                VALUES ('22222222-2222-7000-8000-000000009999',
                        '22222222-2222-7000-8000-000000000001',
                        'leak', 'CUP')
                """.trimIndent(),
                connection,
                "policy", "row-level", "violat",
            )
        }
        asApp(beta).use { connection ->
            connection.scalarLong("SELECT count(*) FROM products") shouldBe 1
            connection.scalarString("SELECT code FROM products") shouldBe "SECRET"
            connection.scalarLong("SELECT count(*) FROM cost_sheets WHERE code = 'FC-PIZZA'") shouldBe 0
        }
    }

    @Test
    fun `I-02 at most one VIGENTE version per cost sheet`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO cost_sheet_versions (
                    organization_id, cost_sheet_id, version_no, status, valid_from,
                    rate_snapshot_id, calc_currency
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000090',
                    99, 'VIGENTE', DATE '2027-01-01',
                    '33333333-3333-7000-8000-000000000001', 'CUP'
                )
                """.trimIndent(),
                connection,
                "uq_cost_sheet_one_vigente", "unique", "duplicate",
            )
        }
    }

    @Test
    fun `I-03 version_no unique per sheet`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO cost_sheet_versions (
                    organization_id, cost_sheet_id, version_no, status, rate_snapshot_id
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000090',
                    1, 'ANULADA', '33333333-3333-7000-8000-000000000001'
                )
                """.trimIndent(),
                connection,
                "unique", "duplicate", "version_no",
            )
        }
    }

    @Test
    fun `I-04 vigente ranges do not overlap`() {
        admin().use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO cost_sheet_versions (
                    organization_id, cost_sheet_id, version_no, status, valid_from, valid_to,
                    rate_snapshot_id, calc_currency
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000090',
                    2, 'REEMPLAZADA', DATE '2025-01-01', DATE '2026-01-01',
                    '33333333-3333-7000-8000-000000000001', 'CUP'
                )
                """.trimIndent(),
            )
            // overlapping with VIGENTE from 2026-01-01
            expectFailure(
                """
                INSERT INTO cost_sheet_versions (
                    organization_id, cost_sheet_id, version_no, status, valid_from, valid_to,
                    rate_snapshot_id, calc_currency
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000090',
                    3, 'REEMPLAZADA', DATE '2026-03-01', DATE '2026-04-01',
                    '33333333-3333-7000-8000-000000000001', 'CUP'
                )
                """.trimIndent(),
                connection,
                "no_overlap", "exclusion", "conflict",
            )
        }
    }

    @Test
    fun `I-05 content is frozen after BORRADOR`() {
        asApp(alpha).use { connection ->
            expectFailure(
                """
                UPDATE cost_sheet_versions
                   SET total_cost = 1
                 WHERE id = '11111111-1111-7000-8000-0000000000a0'
                """.trimIndent(),
                connection,
                "I-05", "frozen",
            )
            expectFailure(
                """
                UPDATE cost_sheet_lines SET quantity = 9
                 WHERE id = '11111111-1111-7000-8000-0000000000b0'
                """.trimIndent(),
                connection,
                "I-05", "frozen",
            )
        }
    }

    @Test
    fun `I-06 only legal status transitions`() {
        asApp(alpha).use { connection ->
            expectFailure(
                """
                UPDATE cost_sheet_versions SET status = 'VIGENTE'
                 WHERE id = '11111111-1111-7000-8000-0000000000a1'
                """.trimIndent(),
                connection,
                "I-06",
            )
            connection.createStatement().execute(
                """
                UPDATE cost_sheet_versions SET status = 'EN_REVISION',
                    rate_snapshot_id = '33333333-3333-7000-8000-000000000001'
                 WHERE id = '11111111-1111-7000-8000-0000000000a1'
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                UPDATE cost_sheet_versions SET status = 'BORRADOR', rate_snapshot_id = NULL
                 WHERE id = '11111111-1111-7000-8000-0000000000a1'
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `I-07 four eyes rejects same actor`() {
        asApp(alpha).use { connection ->
            expectFailure(
                """
                INSERT INTO approval_steps (organization_id, request_id, step_no, role_code, decided_by, decision)
                VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-0000000000f1',
                    2, 'REVISOR',
                    '11111111-1111-7000-8000-000000000031',
                    'APPROVE'
                )
                """.trimIndent(),
                connection,
                "I-07",
            )
        }
    }

    @Test
    fun `I-08 ipv value cannot be physically deleted while referenced`() {
        admin().use { connection ->
            expectFailure(
                "DELETE FROM ipv_values WHERE id = '11111111-1111-7000-8000-000000000080'",
                connection,
                "restrict", "foreign", "ipv_value",
            )
        }
    }

    @Test
    fun `I-09 control line cannot point at a draft or out-of-period version`() {
        asApp(alpha).use { connection ->
            expectFailure(
                """
                INSERT INTO ipv_control_lines (
                    organization_id, control_id, product_id, cost_sheet_version_id,
                    expected_qty, expected_unit_cost
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-0000000000e0',
                    '11111111-1111-7000-8000-000000000071',
                    '11111111-1111-7000-8000-0000000000a1',
                    1, 1
                )
                """.trimIndent(),
                connection,
                "I-09",
            )
        }
    }

    @Test
    fun `I-10 ipv values do not overlap by subject branch and currency`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO ipv_values (
                    organization_id, company_id, raw_material_id, currency, unit_price, valid_from
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000010',
                    '11111111-1111-7000-8000-000000000060',
                    'CUP', 50, DATE '2026-06-01'
                )
                """.trimIndent(),
                connection,
                "no_overlap", "exclusion", "conflict",
            )
        }
    }

    @Test
    fun `I-11 amounts are non-negative and rates positive`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO ipv_values (
                    organization_id, company_id, raw_material_id, currency, unit_price, valid_from
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000010',
                    '11111111-1111-7000-8000-000000000060',
                    'USD', -1, DATE '2027-01-01'
                )
                """.trimIndent(),
                connection,
                "unit_price", "check",
            )
            expectFailure(
                """
                INSERT INTO exchange_rate_samples (instrument_code, value, source, is_test)
                VALUES ('USD', 0, 'MANUAL', false)
                """.trimIndent(),
                connection,
                "check", "value",
            )
        }
    }

    @Test
    fun `I-12 rate samples are immutable`() {
        admin().use { connection ->
            expectFailure(
                "UPDATE exchange_rate_samples SET value = 1 WHERE instrument_code = 'USD'",
                connection,
                "I-12", "append-only",
            )
        }
    }

    @Test
    fun `I-13 snapshots are immutable and unique by hash`() {
        admin().use { connection ->
            expectFailure(
                "UPDATE rate_snapshots SET status_at_capture = 'X' WHERE id = '${SeedIds.SNAPSHOT}'",
                connection,
                "I-13", "append-only",
            )
            expectFailure(
                """
                INSERT INTO rate_snapshots (content_hash, status_at_capture)
                VALUES (decode('${cu.ipvgc.seed.SyntheticSeed.SNAPSHOT_HASH_HEX}', 'hex'), 'TEST')
                """.trimIndent(),
                connection,
                "unique", "duplicate", "content_hash",
            )
        }
    }

    @Test
    fun `I-14 audit is append-only`() {
        asApp(alpha).use { connection ->
            expectFailure(
                "UPDATE audit_events SET action = 'TAMPER' WHERE true",
                connection,
                "I-14", "append-only", "permission",
            )
            expectFailure(
                "DELETE FROM audit_events",
                connection,
                "I-14", "append-only", "permission", "I-16",
            )
        }
        admin().use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO audit_events (organization_id, actor_id, action, result)
                VALUES ('${alpha}', '11111111-1111-7000-8000-000000000030', 'AUTH.LOGIN.SUCCESS', 'SUCCESS')
                """.trimIndent(),
            )
            connection.scalarLong("SELECT max(seq) FROM audit_events WHERE organization_id = '$alpha'") shouldBeGreaterThan 1
        }
    }

    @Test
    fun `I-15 mutations are applied once`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO sync_mutations (
                    mutation_id, organization_id, device_id, seq_no, entity_type, entity_id, op, status
                ) VALUES (
                    '11111111-1111-7000-8000-0000000000ac',
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-0000000000ff',
                    2, 'inventory_movements', '11111111-1111-7000-8000-0000000000d0',
                    'UPSERT', 'APPLIED'
                )
                """.trimIndent(),
                connection,
                "unique", "duplicate", "pk", "sync_mutations",
            )
        }
    }

    @Test
    fun `I-16 physical delete is forbidden for app_rw`() {
        asApp(alpha).use { connection ->
            expectFailure(
                "DELETE FROM companies",
                connection,
                "permission", "I-16",
            )
        }
    }

    @Test
    fun `I-17 inventory movements are append-only`() {
        admin().use { connection ->
            expectFailure(
                "UPDATE inventory_movements SET quantity = 1",
                connection,
                "I-17", "append-only",
            )
        }
    }

    @Test
    fun `I-18 unique keygen id and one active license per user`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO licenses (organization_id, user_id, keygen_license_id, policy_code, status_cached)
                VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000031',
                    'keygen-lic-alpha-admin', 'IPV-MENSUAL', 'ACTIVE'
                )
                """.trimIndent(),
                connection,
                "unique", "duplicate",
            )
            expectFailure(
                """
                INSERT INTO licenses (organization_id, user_id, keygen_license_id, policy_code, status_cached)
                VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-000000000030',
                    'keygen-lic-alpha-admin-2', 'IPV-MENSUAL', 'ACTIVE'
                )
                """.trimIndent(),
                connection,
                "unique", "duplicate", "one_active",
            )
        }
    }

    @Test
    fun `I-19 document numbers are unique and monotonic`() {
        admin().use { connection ->
            val n1 = connection.scalarLong(
                "SELECT app.next_document_number('$alpha', '${SeedIds.COMPANY_ALPHA}', 'IPV', 2026)",
            )
            val n2 = connection.scalarLong(
                "SELECT app.next_document_number('$alpha', '${SeedIds.COMPANY_ALPHA}', 'IPV', 2026)",
            )
            n2 shouldBe n1 + 1
        }
    }

    @Test
    fun `I-20 line currency must belong to the company`() {
        admin().use { connection ->
            expectFailure(
                """
                INSERT INTO cost_sheet_lines (
                    organization_id, version_id, line_no, line_type, raw_material_id, ipv_value_id,
                    quantity, unit_cost_snapshot, unit_currency, line_cost
                ) VALUES (
                    '11111111-1111-7000-8000-000000000001',
                    '11111111-1111-7000-8000-0000000000a1',
                    2, 'MATERIAL',
                    '11111111-1111-7000-8000-000000000061',
                    '11111111-1111-7000-8000-000000000081',
                    1, 1, 'MXN', 1
                )
                """.trimIndent(),
                connection,
                "I-20",
            )
        }
    }
}
