package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException
import java.net.URI
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
  ): ResponseEntity<ServiceData>? = dynamicApiWebClient
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
        renderEvent = GET_SERVICE_DATA_RETRY,
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
        response.toEntity(ServiceData::class.java)
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

  fun getAttachment(
    renderRequest: RenderRequest,
    url: String,
    contentType: String,
    expectedSize: Int,
  ): ByteArray = dynamicApiWebClient.mutate().baseUrl(url).build()
    .get()
    .header(CONTENT_TYPE, contentType)
    .retrieve()
    .onStatus(
      webClientRetriesSpec.is4xxStatus(),
      webClientRetriesSpec.throw4xxStatusFatalError(renderRequest.id!!),
    )
    .bodyToMono(ByteArray::class.java)
    .flatMap { bytes ->
      if (bytes.size != expectedSize) {
        Mono.error(
          WebClientRequestException(
            IllegalStateException("Attachment GET $url expected filesize $expectedSize does not match retrieved data size ${bytes.size}"),
            HttpMethod.GET,
            URI(url),
            HttpHeaders(),
          ),
        )
      } else {
        Mono.just(bytes)
      }
    }
    .retryWhen(
      webClientRetriesSpec.retry5xxAndClientRequestErrors(
        renderRequest = renderRequest,
        renderEvent = GET_ATTACHMENT_RETRY,
        "serviceName" to renderRequest.serviceName!!,
        "uri" to url,
      ),
    ).block()!!

  fun getServiceTemplate(renderRequest: RenderRequest): TemplateResponse? = dynamicApiWebClient
    .mutate()
    .baseUrl(renderRequest.serviceUrl!!)
    .build()
    .get()
    .uri("/subject-access-request/template")
    .exchangeToMono { response ->
      log.info("get template request returned status ${response.statusCode().value()}")
      when {
        response.statusCode().value() == 400 -> {
          Mono.justOrEmpty(null)
        }

        response.statusCode().value() == 200 -> {
          response.bodyToMono(String::class.java)
            .map { body ->
              TemplateResponse(
                serviceName = renderRequest.serviceName,
                version = response.headers()
                  .asHttpHeaders()
                  .getFirst("X-Content-Version")
                  ?.toInt(),
                templateBody = body,
              )
            }
        }

        else -> Mono.error(RuntimeException("unexpected response status error ${response.statusCode().value()}"))
      }
    }
    .block()
}


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
data class ServiceData(
  val content: Any? = null,
  val attachments: List<Attachment>? = null,
)

data class Attachment(
  val attachmentNumber: Int,
  val name: String,
  val contentType: String,
  val url: String,
  val filesize: Int,
  val filename: String,
)

data class TemplateResponse(
  var serviceName: String? = null,
  var version: Int? = null,
  var templateBody: String? = null,
)
