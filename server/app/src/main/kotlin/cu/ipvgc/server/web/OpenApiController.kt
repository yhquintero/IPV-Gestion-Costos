package cu.ipvgc.server.web

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OpenApiController {
    @GetMapping("/api/v1/openapi.yaml", produces = ["application/yaml", MediaType.TEXT_PLAIN_VALUE])
    fun openapi(): String = ClassPathResource("openapi/ipv-gc.yaml").inputStream.use { it.reader().readText() }
}
