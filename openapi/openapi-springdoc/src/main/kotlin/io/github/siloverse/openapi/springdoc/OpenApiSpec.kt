package io.github.siloverse.openapi.springdoc

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fetches the spec a running application describes, for a test that writes it to the repo
 * (the committed `openapi-spec.yaml` is the contract) or checks the committed one for drift.
 */
object OpenApiSpec {

    const val PATH = "/v3/api-docs.yaml"

    fun yaml(mockMvc: MockMvc): String =
        mockMvc.perform(get(PATH)).andReturn().response.contentAsString

    /** Writes the spec. Returns true when the file changed, so a drift check can fail the build. */
    fun write(mockMvc: MockMvc, target: Path): Boolean {
        val yaml = yaml(mockMvc)
        val before = if (Files.exists(target)) Files.readString(target) else null
        Files.createDirectories(target.toAbsolutePath().parent)
        Files.writeString(target, yaml)
        return before != yaml
    }
}
