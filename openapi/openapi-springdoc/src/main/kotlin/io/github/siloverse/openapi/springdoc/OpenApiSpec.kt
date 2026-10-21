package io.github.siloverse.openapi.springdoc

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files
import java.nio.file.Path

/**
 * The committed `openapi-spec.yaml` is the silo's contract. A silo's spec test calls [verify]
 * from its hermetic Boot context, and the build convention wires the two system properties:
 *
 * - `siloverse.openapi.spec`: where the committed contract lives (the repo root by default)
 * - `siloverse.openapi.update`: `true` when the build ran with `-PupdateOpenApiSpec`
 *
 * With update, the file is rewritten from the running application. Without it, the running
 * application is compared to the file and a difference fails the test with the fix. A missing
 * file is written the first time, so a new silo starts without a chicken-and-egg step.
 */
object OpenApiSpec {

    const val PATH = "/v3/api-docs.yaml"
    const val SPEC_PROPERTY = "siloverse.openapi.spec"
    const val UPDATE_PROPERTY = "siloverse.openapi.update"

    /** Fails loudly when the endpoint is not reachable, instead of writing an empty contract. */
    fun yaml(mockMvc: MockMvc): String {
        val response = mockMvc.perform(get(PATH)).andReturn().response
        check(response.status == 200 && response.contentAsString.isNotBlank()) {
            "GET $PATH answered ${response.status} with ${response.contentAsString.length} bytes. " +
                "Permit `/v3/api-docs`, `/v3/api-docs.yaml` and `/v3/api-docs/**` in the security configuration " +
                "(the contract is public: generated clients are built from it)."
        }
        return response.contentAsString
    }

    /** Writes the spec. Returns true when the file changed. */
    fun write(mockMvc: MockMvc, target: Path): Boolean {
        val yaml = yaml(mockMvc)
        val before = if (Files.exists(target)) Files.readString(target) else null
        Files.createDirectories(target.toAbsolutePath().parent)
        Files.writeString(target, yaml)
        return before != yaml
    }

    /** The silo's spec test: one line, `OpenApiSpec.verify(mockMvc)`. */
    fun verify(mockMvc: MockMvc) {
        val target = Path.of(System.getProperty(SPEC_PROPERTY) ?: "openapi-spec.yaml")
        val update = System.getProperty(UPDATE_PROPERTY).toBoolean()
        if (update || !Files.exists(target)) {
            write(mockMvc, target)
            return
        }
        val committed = Files.readString(target)
        val current = yaml(mockMvc)
        if (committed != current) {
            throw AssertionError(
                "openapi-spec.yaml at $target does not match the running application. " +
                    "The committed contract drifted from the code. Run `./gradlew test -PupdateOpenApiSpec` and commit the result.",
            )
        }
    }
}
