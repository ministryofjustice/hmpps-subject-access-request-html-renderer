package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.FileSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestResourceNotFoundException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.UUID

/**
 * Endpoint to retrieve generated html files from the backing S3 bucket. Intended as a developer helper tool to assist
 * the service template creation process.
 *
 * The endpoint is conditional defaulting to false/disabled and must be explicitly enabled. It should NOT be enabled in
 * the Preprod or Production environments.
 */
@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request"], produces = ["text/plain"])
@ConditionalOnExpression("\${developer-endpoint.enabled:false}")
class DeveloperController(private val renderService: RenderService) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(
    summary = "Get the html from document store",
    description = "Get the content of the generated html file from document store",
    security = [SecurityRequirement(name = "subject-access-request-html-renderer-ui-role")],
    responses = [
      ApiResponse(responseCode = "200", description = "Success, returns list of files"),
      ApiResponse(
        responseCode = "404",
        description = "file not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/{subjectAccessRequestId}/{serviceName}", produces = ["text/plain"])
  suspend fun getRenderedHtml2(
    @PathVariable subjectAccessRequestId: String,
    @PathVariable serviceName: String,
  ): ResponseEntity<String> {
    val documentKey = "$subjectAccessRequestId/$serviceName.html"

    return renderService.getRenderedHtml(documentKey)
      ?.let { return ResponseEntity(String(it), null, HttpStatus.OK) }
      ?: throw SubjectAccessRequestResourceNotFoundException(documentKey)
  }

  @Operation(
    summary = "List the files generated for report ID",
    description = "Return a list of the files that has been generated for the specified subject access request ID",
    security = [SecurityRequirement(name = "subject-access-request-html-renderer-ui-role")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Success, returns list of files",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = FileSummary::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No files for the provided subject access request ID found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/{subjectAccessRequestId}", produces = ["application/json"])
  suspend fun listReportFiles(@PathVariable subjectAccessRequestId: String): ResponseEntity<FileSummary> = renderService
    .listCacheFilesWithPrefix(UUID.fromString(subjectAccessRequestId))
    ?.let { ResponseEntity(FileSummary(it), null, HttpStatus.OK) }
    ?: throw SubjectAccessRequestResourceNotFoundException(subjectAccessRequestId)
}
