package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException
import java.util.Optional
import java.util.UUID

@Service
class DynamicServicesClient(
  @Qualifier("dynamicWebClient") private val dynamicApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(DynamicServicesClient::class.java)
  }

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
    .exchangeToMono(processResponse(renderRequest.id, renderRequest.serviceName))
    .retryWhen(
      webClientRetriesSpec.retry5xxAndClientRequestErrors(
        renderRequest = renderRequest,
        "serviceName" to renderRequest.serviceName!!,
        "uri" to renderRequest.serviceUrl,
      ),
    ).block()

  private fun processResponse(
    subjectAccessRequestId: UUID?,
    serviceName: String?,
  ) = { response: ClientResponse ->
    log.info("get subject access request data request returned status:{}", response.statusCode().value())
    when {
      response.statusCode().value() == 204 || response.statusCode().value() == 209 -> {
        Mono.just(ResponseEntity.status(response.statusCode()).build())
      }

      response.statusCode().is2xxSuccessful -> {
        response.toEntity(Map::class.java)
      }

      response.statusCode().is4xxClientError -> {
        Mono.error(
          FatalSubjectAccessRequestException(
            message = "response status: ${response.statusCode().value()} not retryable",
            subjectAccessRequestId = subjectAccessRequestId,
            params = mapOf("service" to serviceName),
          ),
        )
      }

      response.statusCode().is5xxServerError -> {
        val request = response.request()
        Mono.error(
          WebClientRetriesSpec.IsStatus5xxException(
            "${request.method} ${request.uri}, status: ${response.statusCode().value()}",
          ),
        )
      }

      else -> Mono.error(RuntimeException("unexpected response status error ${response.statusCode().value()}"))
    }
  }
}
