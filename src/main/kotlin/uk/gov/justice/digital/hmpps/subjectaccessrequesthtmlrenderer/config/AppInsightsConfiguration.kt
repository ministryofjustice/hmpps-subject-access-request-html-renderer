package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.util.UUID

@Configuration
class AppInsightsConfiguration {

  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

fun TelemetryClient.renderEvent(
  event: RenderEvent,
  id: UUID? = null,
  vararg kvPairs: Pair<String, String> = emptyArray(),
) {
  this.trackEvent(
    event.name,
    mapOf(
      "id" to id.toString(),
      *kvPairs,
    ),
    null,
  )
}

fun TelemetryClient.renderEvent(
  event: RenderEvent,
  request: RenderRequest? = null,
  vararg kvPairs: Pair<String, String> = emptyArray(),
) {
  this.trackEvent(
    event.name,
    mapOf(
      "id" to request?.id.toString(),
      "serviceName" to request?.serviceName,
      *kvPairs,
    ),
    null,
  )
}

enum class RenderEvent {
  REQUEST_RECEIVED,
  REQUEST_COMPLETE,
  REQUEST_ERRORED,
  REQUEST_COMPLETE_HTML_CACHED,
  GET_SERVICE_DATA,
  SERVICE_DATA_RETURNED,
  SERVICE_DATA_NO_CONTENT,
  SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED,
  GET_SERVICE_DATA_RETRY,
  RENDER_TEMPLATE_STARTED,
  RENDER_TEMPLATE_COMPLETED,
  STORE_RENDERED_HTML_STARTED,
  STORE_RENDERED_HTML_COMPLETED,
}
