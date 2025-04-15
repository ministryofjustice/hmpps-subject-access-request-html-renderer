package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException

@Service
class NomisMappingApiClient(
  private val nomisMappingsApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getNomisLocationMapping(nomisLocationId: Int): NomisLocationMapping? = try {
    nomisMappingsApiWebClient
      .get()
      .uri("/api/locations/nomis/$nomisLocationId")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { Mono.error(NomisLocationMappingNotFoundException(nomisLocationId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          params = mapOf("nomisLocationId" to nomisLocationId),
        ),
      )
      .bodyToMono(NomisLocationMapping::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          renderRequest = null,
          "nomisLocationId" to nomisLocationId,
        ),
      )
      // Return null when not found
      .onErrorResume(NomisLocationMappingNotFoundException::class.java) { Mono.empty() }
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "nomisMappingsApiClient error authorization exception",
      cause = ex,
      params = mapOf(
        "cause" to ex.cause?.message,
      ),
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class NomisLocationMapping(
    val dpsLocationId: String,
    val nomisLocationId: Int,
  ) {
    constructor() : this("", 0)
  }

  class NomisLocationMappingNotFoundException(nomisLocationId: Int) : RuntimeException("/api/locations/nomis/$nomisLocationId not found")
}
