package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE_HTML_CACHED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_RECEIVED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderService.RenderResult.CREATED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderService.RenderResult.DATA_ALREADY_EXISTS
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request"], produces = ["application/json"])
class RenderController(
  private val renderService: RenderService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @PostMapping("/render")
  @Operation(
    summary = "Render HTML for subject access request",
    description = "Endpoint renders a subject access request HTML report fragment",
    security = [SecurityRequirement(name = "subject-access-request-html-renderer-ui-role")],
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Template fragment created successfully",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RenderResponse::class))],
      ),
      ApiResponse(responseCode = "204", description = "Cached template fragment exists no action required"),
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
  suspend fun renderTemplate(@RequestBody renderRequest: RenderRequest): ResponseEntity<RenderResponse> {
    try {
      return handleRenderRequest(renderRequest)
    } catch (sarEx: SubjectAccessRequestException) {
      throw sarEx
    } catch (ex: Exception) {
      // wrap exception with additional context to aid debugging
      throw SubjectAccessRequestException(
        message = "render request threw unexpected exception",
        cause = ex,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceName" to renderRequest.serviceName),
      )
    }
  }

  private suspend fun handleRenderRequest(renderRequest: RenderRequest): ResponseEntity<RenderResponse> {
    log.info("Rendering SAR HTML for sar.id={}, serviceName={}", renderRequest.id, renderRequest.serviceName)
    telemetryClient.renderEvent(REQUEST_RECEIVED, renderRequest)

    val response = when (renderService.renderServiceDataHtml(renderRequest)) {
      CREATED -> documentCreatedResponse(renderRequest).also {
        telemetryClient.renderEvent(REQUEST_COMPLETE, renderRequest)
      }

      DATA_ALREADY_EXISTS -> documentAlreadyExistsResponse().also {
        telemetryClient.renderEvent(REQUEST_COMPLETE_HTML_CACHED, renderRequest)
      }
    }

    return response
  }

  private fun documentCreatedResponse(renderRequest: RenderRequest) = ResponseEntity(
    RenderResponse(
      renderRequest.id!!,
      renderRequest.serviceName!!,
    ),
    HttpStatus.CREATED,
  )

  private fun documentAlreadyExistsResponse(): ResponseEntity<RenderResponse> = ResponseEntity.noContent().build()
}
