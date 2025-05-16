package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.GetSubjectAccessRequestDataParams

class HmppsServiceApiMockServer(port: Int) : WireMockServer(port) {

  fun stubGetSubjectAccessRequestData(
    params: GetSubjectAccessRequestDataParams,
    responseDefinition: ResponseDefinitionBuilder,
  ) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(responseDefinition),
    )
  }
}

class HmppsServiceApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsService1Mock = HmppsServiceApiMockServer(8101)
    val hmppsService2Mock = HmppsServiceApiMockServer(8102)
    val hmppsService3Mock = HmppsServiceApiMockServer(8103)
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsService1Mock.start()
    hmppsService2Mock.start()
    hmppsService3Mock.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsService1Mock.resetAll()
    hmppsService2Mock.resetAll()
    hmppsService3Mock.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsService1Mock.stop()
    hmppsService2Mock.stop()
    hmppsService3Mock.stop()
  }
}
