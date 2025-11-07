package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestBadRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestResourceNotFoundException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestServiceConfigurationNotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestControllerAdvice
class SubjectAccessRequestHtmlRendererExceptionHandler(
  private val telemetryClient: TelemetryClient,
) {
  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, BAD_REQUEST)
      log.info("Validation exception: {}", e.message)
    }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, NOT_FOUND)
      log.info("No resource found exception: {}", e.message)
    }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(FORBIDDEN)
    .body(
      ErrorResponse(
        status = FORBIDDEN,
        userMessage = "Forbidden: ${e.message}",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, FORBIDDEN)
      log.debug("Forbidden (403) returned: {}", e.message)
    }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        userMessage = e.message,
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, INTERNAL_SERVER_ERROR)
      log.error("Unexpected exception", e)
    }

  @ExceptionHandler
  fun handleSubjectAccessRequestResourceNotFoundException(
    e: SubjectAccessRequestResourceNotFoundException,
  ): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "resource not found: ${e.message}",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, NOT_FOUND)
      log.error("Subject access request resource not found exception", e)
    }

  @ExceptionHandler
  fun handleSubjectAccessRequestServiceConfigurationNotFoundException(
    e: SubjectAccessRequestServiceConfigurationNotFoundException,
  ): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "service configuration ID: ${e.serviceConfigurationId} not found",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, NOT_FOUND)
      log.error("service configuration ID: ${e.serviceConfigurationId} not found", e)
    }

  @ExceptionHandler
  fun handleSubjectAccessRequestBadRequestException(
    e: SubjectAccessRequestBadRequestException,
  ): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "bad request: ${e.message}",
        developerMessage = e.message,
      ),
    ).also {
      telemetryClient.trackRenderException(e, BAD_REQUEST)
      log.error("bad request: ${e.message}", e)
    }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun TelemetryClient.trackRenderException(e: Exception, status: HttpStatus) {
    if (e is SubjectAccessRequestException) {
      telemetryClient.renderEvent(
        event = RenderEvent.REQUEST_ERRORED,
        id = e.subjectAccessRequestId,
        "status" to status.value().toString(),
        "errorMessage" to e.messageOrDefault(),
        *e.paramsToPairsArray(),
      )
    } else {
      telemetryClient.renderEvent(
        event = RenderEvent.REQUEST_ERRORED,
        request = null,
        "error" to (e.message ?: ""),
        "status" to status.value().toString(),
      )
    }
  }
}
