package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.RendererServiceFailureType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.ServiceErrorNotificationRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.SubjectAccessRequestApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.util.UUID

class ServiceCallFailureNotificationServiceTest {

  private val subjectAccessRequestApiClient: SubjectAccessRequestApiClient = mock()

  private val serviceCallFailureNotificationService = ServiceCallFailureNotificationService(subjectAccessRequestApiClient)

  private val renderRequest = RenderRequest(
    id = UUID.randomUUID(),
    serviceConfiguration = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = "hmpps-test-service",
      label = "HMPPS Test Service",
      enabled = true,
      templateMigrated = true,
      url = "http://localhost:8092",
      category = ServiceCategory.PRISON,
    ),
  )

  @Test
  fun `should report service call failure with status code from http status param`() {
    val exception = FatalSubjectAccessRequestException(
      message = "response status: 400 not retryable",
      subjectAccessRequestId = renderRequest.id,
      params = mapOf("httpStatus" to HttpStatus.BAD_REQUEST),
    )

    serviceCallFailureNotificationService.notifyServiceCallFailure(
      renderRequest,
      RendererServiceFailureType.SAR_DATA,
      exception,
    )

    verify(subjectAccessRequestApiClient).reportServiceError(
      eq(renderRequest.id!!),
      eq(
        ServiceErrorNotificationRequest(
          serviceName = renderRequest.serviceConfiguration.serviceName,
          failureType = RendererServiceFailureType.SAR_DATA,
          statusCode = 400,
          message = exception.message,
        ),
      ),
    )
  }

  @Test
  fun `should report service call failure with status code from retry exhausted cause message`() {
    val exception = SubjectAccessRequestRetryExhaustedException(
      retryAttempts = 2,
      cause = RuntimeException("GET http://localhost:8092/subject-access-request, status: 500"),
      subjectAccessRequestId = renderRequest.id,
      params = mapOf("uri" to "http://localhost:8092/subject-access-request"),
    )

    serviceCallFailureNotificationService.notifyServiceCallFailure(
      renderRequest,
      RendererServiceFailureType.TEMPLATE,
      exception,
    )

    verify(subjectAccessRequestApiClient).reportServiceError(
      eq(renderRequest.id!!),
      eq(
        ServiceErrorNotificationRequest(
          serviceName = renderRequest.serviceConfiguration.serviceName,
          failureType = RendererServiceFailureType.TEMPLATE,
          statusCode = 500,
          message = exception.message,
        ),
      ),
    )
  }

  @Test
  fun `should swallow callback client failures`() {
    val exception = FatalSubjectAccessRequestException(
      message = "response status: 400 not retryable",
      subjectAccessRequestId = renderRequest.id,
      params = mapOf("httpStatus" to HttpStatus.BAD_REQUEST),
    )

    doThrow(RuntimeException("boom")).whenever(subjectAccessRequestApiClient)
      .reportServiceError(eq(renderRequest.id!!), any())

    assertDoesNotThrow {
      serviceCallFailureNotificationService.notifyServiceCallFailure(
        renderRequest,
        RendererServiceFailureType.SAR_DATA,
        exception,
      )
    }
  }
}
