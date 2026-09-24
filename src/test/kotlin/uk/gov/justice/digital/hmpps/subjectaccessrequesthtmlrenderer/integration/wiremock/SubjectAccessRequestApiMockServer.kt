package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.RendererServiceFailureType
import java.util.UUID

class SubjectAccessRequestApiMockServer : WireMockServer(8093) {

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubReportServiceError(responseDefinitionBuilder: ResponseDefinitionBuilder = aResponse().withStatus(204)) {
    stubFor(
      post(urlPathMatching("/api/subjectAccessRequests/.+/service-errors"))
        .willReturn(responseDefinitionBuilder),
    )
  }

  fun verifyReportServiceErrorCalled(
    subjectAccessRequestId: UUID,
    serviceName: String,
    failureType: RendererServiceFailureType,
    statusCode: Int? = null,
    message: String? = null,
    times: Int = 1,
  ) {
    var requestPattern = postRequestedFor(urlEqualTo("/api/subjectAccessRequests/$subjectAccessRequestId/service-errors"))
      .withRequestBody(matchingJsonPath("$.serviceName", equalTo(serviceName)))
      .withRequestBody(matchingJsonPath("$.failureType", equalTo(failureType.name)))

    statusCode?.let {
      requestPattern = requestPattern.withRequestBody(matchingJsonPath("$.statusCode", equalTo(it.toString())))
    }
    message?.let {
      requestPattern = requestPattern.withRequestBody(matchingJsonPath("$.message", equalTo(it)))
    }

    verify(times, requestPattern)
  }
}

class SubjectAccessRequestApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val subjectAccessRequestApi = SubjectAccessRequestApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = subjectAccessRequestApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = subjectAccessRequestApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = subjectAccessRequestApi.stop()
}
