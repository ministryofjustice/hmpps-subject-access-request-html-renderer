package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.RendererServiceFailureType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.ServiceErrorNotificationRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.SubjectAccessRequestApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest

@Service
class ServiceCallFailureNotificationService(
  private val subjectAccessRequestApiClient: SubjectAccessRequestApiClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(ServiceCallFailureNotificationService::class.java)
    private val statusCodeRegex = Regex("""\bstatus:\s*(\d{3})\b""")
  }

  fun notifyServiceCallFailure(
    renderRequest: RenderRequest,
    failureType: RendererServiceFailureType,
    exception: SubjectAccessRequestException,
  ) {
    val subjectAccessRequestId = renderRequest.id ?: return

    try {
      subjectAccessRequestApiClient.reportServiceError(
        subjectAccessRequestId = subjectAccessRequestId,
        request = ServiceErrorNotificationRequest(
          serviceName = renderRequest.serviceConfiguration.serviceName,
          failureType = failureType,
          statusCode = exception.statusCode(),
          message = exception.message,
        ),
      )
    } catch (notificationEx: Exception) {
      log.warn(
        "failed to notify subject access request api of renderer service call failure: sar.id={}, serviceName={}, failureType={}",
        subjectAccessRequestId,
        renderRequest.serviceConfiguration.serviceName,
        failureType,
        notificationEx,
      )
    }
  }

  private fun SubjectAccessRequestException.statusCode(): Int? = statusCodeFromParams()
    ?: (cause as? WebClientResponseException)?.statusCode?.value()
    ?: cause?.message?.statusCodeFromMessage()
    ?: message?.statusCodeFromMessage()

  private fun SubjectAccessRequestException.statusCodeFromParams(): Int? = listOfNotNull(
    params?.get("status"),
    params?.get("httpStatus"),
  ).firstNotNullOfOrNull { value ->
    when (value) {
      is Int -> value
      is HttpStatusCode -> value.value()
      else -> value.toString().substringBefore(" ").toIntOrNull()
    }
  }

  private fun String.statusCodeFromMessage(): Int? = statusCodeRegex.find(this)?.groupValues?.get(1)?.toIntOrNull()
}
