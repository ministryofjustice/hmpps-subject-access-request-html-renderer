package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.GetSubjectAccessRequestDataParams

class SarDataSourceApiMockServer : WireMockServer(8092) {

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

  fun stubGetSubjectAccessRequestDataSuccess(params: GetSubjectAccessRequestDataParams, responseBody: String) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody),
        ),
    )
  }

  fun stubGetSubjectAccessRequestDataEmpty(params: GetSubjectAccessRequestDataParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun verifyGetSubjectAccessRequestDataCalled(times: Int) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/subject-access-request")),
  )

  fun verifyGetSubjectAccessRequestDataNeverCalled() = verify(
    0,
    getRequestedFor(urlPathEqualTo("/subject-access-request")),
  )
}

class SarDataSourceApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val sarDataSourceApi = SarDataSourceApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = sarDataSourceApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = sarDataSourceApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = sarDataSourceApi.stop()
}
