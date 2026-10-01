package cu.ipvgc.server.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/actuator/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")
}
