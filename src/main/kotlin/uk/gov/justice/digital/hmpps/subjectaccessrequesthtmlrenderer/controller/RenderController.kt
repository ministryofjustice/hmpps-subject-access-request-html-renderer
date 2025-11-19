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
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequestEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestBadRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestNotFoundException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderService.RenderResult.CREATED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderService.RenderResult.DATA_ALREADY_EXISTS
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request"], produces = ["application/json"])
class RenderController(
  private val renderService: RenderService,
  private val telemetryClient: TelemetryClient,
  private val serviceConfigurationService: ServiceConfigurationService,
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
  suspend fun renderTemplate(@RequestBody renderRequest: RenderRequestEntity): ResponseEntity<RenderResponse> {
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
        params = mapOf("serviceConfigurationId" to renderRequest.serviceConfigurationId),
      )
    }
  }

  private suspend fun handleRenderRequest(entity: RenderRequestEntity): ResponseEntity<RenderResponse> {
    validateRequest(entity)
    val serviceConfiguration = getServiceConfiguration(entity)
    val renderRequest = RenderRequest(entity, serviceConfiguration)

    log.info("Rendering SAR HTML for sar.id={}, serviceName={}", renderRequest.id, serviceConfiguration.serviceName)
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

  private fun validateRequest(request: RenderRequestEntity) {
    if (request.id == null) {
      throw SubjectAccessRequestBadRequestException("request.id was null")
    }
    if (request.serviceConfigurationId == null) {
      throw SubjectAccessRequestBadRequestException("request.serviceConfigurationId was null", request.id)
    }
    if (request.nomisId.isNullOrEmpty() && request.ndeliusId.isNullOrEmpty()) {
      throw SubjectAccessRequestBadRequestException(
        "request.nomisId and request.ndeliusId was null or empty",
        request.id,
      )
    }
    if (request.dateTo == null) {
      throw SubjectAccessRequestBadRequestException("request.dateTo was null", request.id)
    }
    if (request.dateFrom != null && request.dateTo!!.isBefore(request.dateFrom)) {
      throw SubjectAccessRequestBadRequestException("request.dateTo is before request.dateFrom", request.id)
    }
    if (request.sarCaseReferenceNumber.isNullOrEmpty()) {
      throw SubjectAccessRequestBadRequestException("request.sarCaseReferenceNumber was null or empty", request.id)
    }
  }

  private fun getServiceConfiguration(
    request: RenderRequestEntity,
  ): ServiceConfiguration = serviceConfigurationService.findByIdOrNull(request.serviceConfigurationId!!)
    ?: throw SubjectAccessRequestNotFoundException(
      subjectAccessRequestId = request.id,
      params = mapOf("serviceConfigurationId" to request.serviceConfigurationId),
    )

  private fun documentCreatedResponse(renderRequest: RenderRequest) = ResponseEntity(
    RenderResponse(
      renderRequest.id!!,
      renderRequest.serviceConfiguration.serviceName,
    ),
    HttpStatus.CREATED,
  )

  private fun documentAlreadyExistsResponse(): ResponseEntity<RenderResponse> = ResponseEntity.noContent().build()
}
