package cu.ipvgc.android.core.network

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class LoginRequest(val email: String, val password: String)

data class MfaRequest(val challenge_id: String, val code: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val mfa_required: Boolean = false,
    val challenge_id: String? = null,
    val user: MeDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MeDto(
    val id: String,
    val organization_id: String? = null,
    val email: String,
    val display_name: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CatalogItemDto(
    val id: String,
    val code: String,
    val name: String,
    val kind: String? = null,
    val company_id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IpvValueDto(
    val id: String,
    val currency: String,
    val unit_price: BigDecimal,
    val source_ref: String? = null,
    val raw_material_id: String? = null,
    val valid_from: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CostSheetDto(
    val id: String,
    val code: String,
    val product_id: String? = null,
    val current_version: CostSheetVersionDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CostSheetCreatedDto(
    val id: String,
    val version_id: String? = null,
    val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CostSheetVersionDto(
    val id: String? = null,
    val cost_sheet_id: String? = null,
    val status: String? = null,
    val total_cost: BigDecimal? = null,
    val unit_cost: BigDecimal? = null,
    val etag: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IpvControlDto(
    val id: String,
    val control_no: String? = null,
    val status: String? = null,
    val mode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RateDto(
    val instrument: String,
    val value: String,
    val status: String? = null,
    val source: String? = null,
    @JsonProperty("is_test") val isTest: Boolean = false,
    val label: String? = null,
)
