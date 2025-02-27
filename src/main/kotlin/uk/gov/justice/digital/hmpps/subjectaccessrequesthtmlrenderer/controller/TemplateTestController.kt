package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/developer/template-test"], produces = ["text/plain"])
class TemplateTestController(
  private val renderService: RenderService,
) {

  @PostMapping("/render", produces = ["text/plain"])
  @Operation(
    summary = "Render the provided data as subject access request HTML",
    description = "Endpoint renders a subject access request HTML report fragment from data provided. Intended for " +
      "development use only - testing out template changes etc.",
    security = [SecurityRequirement(name = "subject-access-request-html-renderer-ui-role")],
    responses = [
      ApiResponse(responseCode = "201", description = "Template fragment created successfully"),
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
  fun test(@RequestBody renderReq: DeveloperRenderRequest): ResponseEntity<String> {
    val html = renderService.renderServiceDataHtmlForDev(renderReq.serviceName!!, renderReq.data!!)
    return ResponseEntity<String>(html, null, HttpStatus.OK)
  }

  data class DeveloperRenderRequest(val serviceName: String? = "", val data: Map<*, *>?) {
    constructor() : this("default", null)
  }
}
