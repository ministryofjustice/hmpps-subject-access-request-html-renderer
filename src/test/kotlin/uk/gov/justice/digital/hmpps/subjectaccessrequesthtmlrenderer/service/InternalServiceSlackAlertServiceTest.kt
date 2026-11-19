package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalServiceSlackAlertServiceTest {
  private val slack = WireMockServer(options().dynamicPort())
  private val requestId = UUID.randomUUID()

  @BeforeAll
  fun start() {
    slack.start()
  }

  @AfterAll
  fun stop() {
    slack.stop()
  }

  @Test
  fun `should inject configured values and pass them to the shared Slack alert sender`() {
    slack.stubFor(post(urlEqualTo("/chat.postMessage")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"ok":true}""")))

    ApplicationContextRunner()
      .withInitializer(ConfigDataApplicationContextInitializer())
      .withUserConfiguration(InternalServiceSlackAlertService::class.java)
      .withPropertyValues(
        "spring.config.location=classpath:/application.yml",
        "slack.api-url=${slack.baseUrl()}",
        "SAR_SLACK_BOT_AUTH_TOKEN=test-token",
        "DEV_HELP_SLACK_CHANNEL_ID=HAAR_CHANNEL",
        "SENTRY_ENV=test",
      )
      .run { context ->
        context.getBean(InternalServiceSlackAlertService::class.java)
          .sendServiceCallFailedAlert("SAR API", "notify", requestId, "test-service")
      }

    slack.verify(
      1,
      postRequestedFor(urlEqualTo("/chat.postMessage"))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withRequestBody(matchingJsonPath("$.channel", equalTo("HAAR_CHANNEL")))
        .withRequestBody(
          matchingJsonPath(
            "$.text",
            equalTo(
              "Subject Access Request: Internal Service Call Failed\nEnvironment: test\nSource: hmpps-subject-access-request-html-renderer\n" +
                "Destination: SAR API\nOperation: notify\nSAR ID: $requestId\nService: test-service\n" +
                "Please investigate the service logs.",
            ),
          ),
        ),
    )
  }
}
