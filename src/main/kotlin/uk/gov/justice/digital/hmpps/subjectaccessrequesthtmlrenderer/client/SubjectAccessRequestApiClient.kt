package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID

@Service
class SubjectAccessRequestApiClient(
  @param:Qualifier("subjectAccessRequestApiNotificationWebClient") private val subjectAccessRequestApiWebClient: WebClient,
) {

  fun reportServiceError(
    subjectAccessRequestId: UUID,
    request: ServiceErrorNotificationRequest,
  ) {
    subjectAccessRequestApiWebClient
      .post()
      .uri("/api/subjectAccessRequests/{id}/service-errors", subjectAccessRequestId)
      .bodyValue(request)
      .retrieve()
      .toBodilessEntity()
      .block()
  }
}

data class ServiceErrorNotificationRequest(
  val serviceName: String,
  val failureType: RendererServiceFailureType,
  val statusCode: Int? = null,
  val message: String? = null,
)

enum class RendererServiceFailureType {
  SAR_DATA,
  ATTACHMENTS,
  TEMPLATE,
}
