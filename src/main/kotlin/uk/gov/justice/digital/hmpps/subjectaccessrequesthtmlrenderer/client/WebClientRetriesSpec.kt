package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestRetryExhaustedException
import java.time.Duration
import java.util.UUID
import java.util.function.Predicate

@Component
class WebClientRetriesSpec(
  private val webClientConfiguration: WebClientConfiguration,
  private val telemetryClient: TelemetryClient,
) {

  private val maxRetries: Long = webClientConfiguration.maxRetries
  private val backOff: Duration = webClientConfiguration.getBackoffDuration()

  companion object {
    private val log = LoggerFactory.getLogger(WebClientRetriesSpec::class.java)
  }

  class IsStatus5xxException(message: String) : RuntimeException(message)

  fun retry5xxAndClientRequestErrors(
    renderRequest: RenderRequest? = null,
    vararg params: Pair<String, Any>,
  ): RetryBackoffSpec = Retry
    .backoff(maxRetries, backOff)
    .filter { err -> is5xxOrClientRequestError(err) || err is IsStatus5xxException }
    .doBeforeRetry { signal ->
      val properties = listOf(
        *params,
        "error" to signal.failure().message,
        "retryAttempts" to signal.totalRetries(),
        "backOff" to backOff,
        "maxRetries" to maxRetries,
      )
      telemetryClient.renderEvent(
        GET_SERVICE_DATA_RETRY,
        renderRequest,
        *properties.map { Pair(it.first, it.second.toString()) }.toTypedArray(),
      )
      log.error(
        "render request id={} failed with error={}, attempting retry after backoff: {}. {}",
        renderRequest?.id,
        signal.failure().message,
        backOff,
        properties.joinToString(","),
      )
    }
    .onRetryExhaustedThrow { _, signal ->
      SubjectAccessRequestRetryExhaustedException(
        retryAttempts = signal.totalRetries(),
        cause = signal.failure(),
        subjectAccessRequestId = renderRequest?.id,
        params = params.toMap(),
      )
    }

  fun is5xxOrClientRequestError(error: Throwable): Boolean = error is WebClientResponseException &&
    error.statusCode.is5xxServerError ||
    error is WebClientRequestException

  fun is4xxStatus(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode ->
    code.is4xxClientError
  }

  fun throw4xxStatusFatalError(
    subjectAccessRequestId: UUID? = null,
    params: Map<String, Any>? = null,
  ) = { response: ClientResponse ->
    val moddedParams = buildMap<String, Any> {
      params?.let { putAll(it) }
      putIfAbsent("uri", response.request().uri.toString())
      putIfAbsent("httpStatus", response.statusCode())
    }

    Mono.error<SubjectAccessRequestException>(
      FatalSubjectAccessRequestException(
        message = "client 4xx response status",
        subjectAccessRequestId = subjectAccessRequestId,
        params = moddedParams,
      ),
    )
  }
}
