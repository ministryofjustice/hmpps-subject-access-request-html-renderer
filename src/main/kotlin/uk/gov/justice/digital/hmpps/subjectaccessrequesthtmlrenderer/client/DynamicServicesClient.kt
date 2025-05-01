package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.util.Optional

@Service
class DynamicServicesClient(
  @Qualifier("dynamicWebClient") private val dynamicApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getSubjectAccessRequestData(
    renderRequest: RenderRequest,
  ): ResponseEntity<Map<*, *>>? = dynamicApiWebClient
    .mutate()
    .baseUrl(renderRequest.serviceUrl!!)
    .build()
    .get()
    .uri {
      it.path("/subject-access-request")
        .queryParamIfPresent("prn", Optional.ofNullable(renderRequest.nomisId))
        .queryParamIfPresent("crn", Optional.ofNullable(renderRequest.ndeliusId))
        .queryParamIfPresent("fromDate", Optional.ofNullable(renderRequest.dateFrom))
        .queryParamIfPresent("toDate", Optional.ofNullable(renderRequest.dateTo))
        .build()
    }
    .retrieve()
    .onStatus(
      webClientRetriesSpec.is4xxStatus(),
      webClientRetriesSpec.throw4xxStatusFatalError(renderRequest.id!!),
    )
    .toEntity(Map::class.java)
    .retryWhen(
      webClientRetriesSpec.retry5xxAndClientRequestErrors(
        renderRequest = renderRequest,
        "serviceName" to renderRequest.serviceName!!,
        "uri" to renderRequest.serviceUrl,
      ),
    ).block()
}
