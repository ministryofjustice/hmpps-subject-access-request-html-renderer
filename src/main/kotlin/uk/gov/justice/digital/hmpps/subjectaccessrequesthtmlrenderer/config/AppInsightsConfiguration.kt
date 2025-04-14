package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest

@Configuration
class AppInsightsConfiguration {

  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

fun TelemetryClient.renderEvent(
  event: RenderEvent,
  request: RenderRequest,
  vararg kvPairs: Pair<String, String> = emptyArray(),
) {
  this.trackEvent(
    event.name,
    mapOf(
      "id" to request.id.toString(),
      "serviceName" to request.serviceName,
      *kvPairs,
    ),
    null,
  )
}

enum class RenderEvent {
  REQUEST_RECEIVED,
  REQUEST_COMPLETE_HTML_RENDERED,
  REQUEST_COMPLETE_HTML_CACHED,
}
