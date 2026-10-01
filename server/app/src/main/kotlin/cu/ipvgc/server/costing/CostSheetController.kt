package cu.ipvgc.server.costing

import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cost-sheets")
class CostSheetController(private val service: CostSheetService) {
    @GetMapping
    fun list() = service.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: CreateSheetRequest) = service.create(body)

    @GetMapping("/{sheetId}/versions/{versionId}")
    fun get(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
    ): ResponseEntity<Map<String, Any?>> {
        val body = service.getVersion(sheetId, versionId)
        val etag = body["etag"]?.toString() ?: "0"
        return ResponseEntity.ok().eTag(etag).body(body)
    }

    @PatchMapping("/{sheetId}/versions/{versionId}")
    fun patch(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestBody body: PatchDraftRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap { service.patchDraft(sheetId, versionId, body, ifMatch?.toLongOrNull()) }

    @PostMapping("/{sheetId}/versions/{versionId}/prevalidate")
    fun prevalidate(@PathVariable sheetId: UUID, @PathVariable versionId: UUID) =
        service.prevalidate(sheetId, versionId)

    @PostMapping("/{sheetId}/versions/{versionId}/submit")
    fun submit(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap { service.submit(sheetId, versionId, ifMatch?.toLongOrNull()) }

    @PostMapping("/{sheetId}/versions/{versionId}/validate")
    fun validate(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap { service.validate(sheetId, versionId, ifMatch?.toLongOrNull()) }

    @PostMapping("/{sheetId}/versions/{versionId}/approve")
    fun approve(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap { service.approve(sheetId, versionId, ifMatch?.toLongOrNull()) }

    @PostMapping("/{sheetId}/versions/{versionId}/activate")
    fun activate(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestBody body: CommentRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap {
        service.activate(
            sheetId,
            versionId,
            body.valid_from ?: throw ApiException.badRequest("valid_from", "valid_from is required"),
            ifMatch?.toLongOrNull(),
        )
    }

    @PostMapping("/{sheetId}/versions/{versionId}/return")
    fun returnDraft(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestBody body: CommentRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap {
        service.returnToDraft(sheetId, versionId, body.comment ?: "", ifMatch?.toLongOrNull())
    }

    @PostMapping("/{sheetId}/versions/{versionId}/annul")
    fun annul(
        @PathVariable sheetId: UUID,
        @PathVariable versionId: UUID,
        @RequestBody body: CommentRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ) = wrap {
        service.annul(sheetId, versionId, body.reason ?: body.comment ?: "", ifMatch?.toLongOrNull())
    }

    @PostMapping("/{sheetId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    fun newVersion(
        @PathVariable sheetId: UUID,
        @RequestBody body: Map<String, String>,
    ) = wrap {
        val from = UUID.fromString(body["from_version_id"] ?: throw ApiException.badRequest("from_version_id", "required"))
        service.newVersion(sheetId, from)
    }

    private fun wrap(block: () -> Map<String, Any?>): Map<String, Any?> = try {
        block()
    } catch (ex: IllegalArgumentException) {
        throw ApiException.conflict("illegal_transition", ex.message ?: "illegal transition")
    }
}
