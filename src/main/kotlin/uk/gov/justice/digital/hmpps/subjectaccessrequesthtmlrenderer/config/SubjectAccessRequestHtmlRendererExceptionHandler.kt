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
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
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

  @ExceptionHandler(SubjectAccessRequestException::class)
  fun handleSubjectAccessRequestException(e: SubjectAccessRequestException): ResponseEntity<ErrorResponse> {
    val status = e.errorCode.resolveToStatusCode()

    return ResponseEntity
      .status(status)
      .body(
        ErrorResponse(
          status = status,
          userMessage = e.message,
          developerMessage = e.message,
          errorCode = e.errorCode.codeString(),
        ),
      ).also {
        telemetryClient.trackRenderException(e, status)
        log.error("Unexpected exception", e)
      }
  }

  private fun ErrorCode.resolveToStatusCode(): HttpStatus = when (this) {
    ErrorCode.BAD_REQUEST -> BAD_REQUEST
    ErrorCode.NOT_FOUND -> NOT_FOUND
    ErrorCode.SERVICE_CONFIGURATION_NOT_FOUND -> NOT_FOUND
    else -> INTERNAL_SERVER_ERROR
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun TelemetryClient.trackRenderException(e: Exception, status: HttpStatus) {
    if (e is SubjectAccessRequestException) {
      this.renderEvent(
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
