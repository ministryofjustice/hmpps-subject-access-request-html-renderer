package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @Value("\${locations-api.url}") val locationsApiBaseUri: String,
  @Value("\${nomis-mappings-api.url}") val nomisMappingsApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
  @Value("\${web-client.configuration.max-retries:0}") val maxRetries: Long,
  @Value("\${web-client.configuration.back-off:PT10S}") val backOff: String,
) {
  // HMPPS Auth health ping is required if your service calls HMPPS Auth to get a token to call other services
  // TODO: Remove the health ping if no call outs to other services are made
  @Bean
  fun hmppsAuthHealthWebClient(
    builder: WebClient.Builder,
  ): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun dynamicWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.codecs { configurer ->
    configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)
  }.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "sar-html-renderer-client",
    url = "http",
    timeout = timeout,
  )

  @Bean
  fun locationsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(locationsApiBaseUri, healthTimeout)

  @Bean
  fun locationsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-html-renderer-client", url = locationsApiBaseUri, timeout)

  @Bean
  fun nomisMappingsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(nomisMappingsApiBaseUri, healthTimeout)

  @Bean
  fun nomisMappingsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-html-renderer-client", url = nomisMappingsApiBaseUri, timeout)

  private var backOffDuration: Duration = Duration.parse(backOff)

  fun getBackoffDuration() = backOffDuration
}
