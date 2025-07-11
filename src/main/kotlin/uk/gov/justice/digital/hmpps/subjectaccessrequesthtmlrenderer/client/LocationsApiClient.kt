package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_LOCATION_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException

@Service
class LocationsApiClient(
  private val locationsApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getLocationDetails(dpsLocationId: String): LocationDetailsResponse? = try {
    locationsApiWebClient
      .get()
      .uri("/locations/$dpsLocationId")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { Mono.error(LocationNotFoundException(dpsLocationId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          params = mapOf("dpsLocationId" to dpsLocationId),
        ),
      )
      .bodyToMono(LocationDetailsResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          renderRequest = null,
          renderEvent = GET_LOCATION_RETRY,
          "dpsLocationId" to dpsLocationId,
        ),
      )
      // Return null response when not found
      .onErrorResume(LocationNotFoundException::class.java) { Mono.empty() }
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "locationsApiClient error authorization exception",
      cause = ex,
      params = mapOf(
        "cause" to ex.cause?.message,
      ),
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class LocationDetailsResponse(
    val id: String,
    val localName: String?,
    val pathHierarchy: String,
  ) {
    constructor() : this("", null, "")
  }

  class LocationNotFoundException(dpsLocationId: String) : RuntimeException("/locations/$dpsLocationId not found")
}
