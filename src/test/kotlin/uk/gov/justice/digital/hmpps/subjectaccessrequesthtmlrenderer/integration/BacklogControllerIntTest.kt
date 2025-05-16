package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.DataHeldSummaryRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsServiceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsServiceApiExtension.Companion.hmppsService1Mock
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsServiceApiExtension.Companion.hmppsService2Mock
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsServiceApiExtension.Companion.hmppsService3Mock
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsServiceApiMockServer
import java.time.LocalDate

@ExtendWith(HmppsServiceApiExtension::class)
class BacklogControllerIntTest : IntegrationTestBase() {

  private val services = listOf(
    DpsServiceWrapper("court-case-service", hmppsService1Mock),
    DpsServiceWrapper("create-and-vary-a-licence-api", hmppsService2Mock),
    DpsServiceWrapper("hmpps-accredited-programmes-api", hmppsService3Mock),
  )

  @Test
  fun `should return data held response when all services hold data on subject`() {
    val getSummaryRequest = dataHeldSummaryRequest(services)

    hmppsAuthReturnsValidAuthToken()

    services.forEach {
      it.returnsData(
        params = getSummaryRequest.toGetSubjectAccessRequestDataParams(),
        responseBody = getServiceResponseBody(it.name),
      )
    }

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(true)
    responseBodySpec.jsonPath("$.dataHeldSummary").isArray
    responseBodySpec.jsonPath("$.dataHeldSummary.length()").isEqualTo(getSummaryRequest.services.size)

    getSummaryRequest.services.forEachIndexed { index, service ->
      responseBodySpec.jsonPath("$.dataHeldSummary[$index].serviceName").isEqualTo(service.name)
      responseBodySpec.jsonPath("$.dataHeldSummary[$index].dataHeld").isEqualTo(true)
    }
  }

  @Test
  fun `should return data not held response when no services hold data on subject`() {
    val getSummaryRequest = dataHeldSummaryRequest(services)

    hmppsAuthReturnsValidAuthToken()
    services.forEach { s -> s.returnsNoData(getSummaryRequest.toGetSubjectAccessRequestDataParams()) }

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(false)
    responseBodySpec.jsonPath("$.dataHeldSummary").isArray
    responseBodySpec.jsonPath("$.dataHeldSummary.length()").isEqualTo(getSummaryRequest.services.size)

    getSummaryRequest.services.forEachIndexed { index, service ->
      responseBodySpec.jsonPath("$.dataHeldSummary[$index].serviceName").isEqualTo(service.name)
      responseBodySpec.jsonPath("$.dataHeldSummary[$index].dataHeld").isEqualTo(false)
    }
  }

  @Test
  fun `should return data held response when some services hold data on subject`() {
    val getSummaryRequest = dataHeldSummaryRequest(services)
    val params = getSummaryRequest.toGetSubjectAccessRequestDataParams()

    hmppsAuthReturnsValidAuthToken()

    services[0].returnsData(params = params, responseBody = getServiceResponseBody(services[0].name))
    services[1].returnsNoData(params)
    services[2].returnsNoData(params)

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(true)
    responseBodySpec.jsonPath("$.dataHeldSummary").isArray
    responseBodySpec.jsonPath("$.dataHeldSummary.length()").isEqualTo(getSummaryRequest.services.size)

    responseBodySpec.jsonPath("$.dataHeldSummary[0].serviceName").isEqualTo(services[0].name)
    responseBodySpec.jsonPath("$.dataHeldSummary[0].dataHeld").isEqualTo(true)

    responseBodySpec.jsonPath("$.dataHeldSummary[1].serviceName").isEqualTo(services[1].name)
    responseBodySpec.jsonPath("$.dataHeldSummary[1].dataHeld").isEqualTo(false)

    responseBodySpec.jsonPath("$.dataHeldSummary[2].serviceName").isEqualTo(services[2].name)
    responseBodySpec.jsonPath("$.dataHeldSummary[2].dataHeld").isEqualTo(false)
  }

  fun sendDataHeldSummaryRequest(
    request: DataHeldSummaryRequest,
  ): WebTestClient.BodyContentSpec = webTestClient.post()
    .uri("/subject-access-request/data-held-summary")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody()

  private fun dataHeldSummaryRequest(services: List<DpsService>) = DataHeldSummaryRequest(
    nomisId = "some-nomis-id",
    ndeliusId = "some-ndelius-id",
    dateFrom = LocalDate.parse("2021-01-01"),
    dateTo = LocalDate.parse("2022-01-01"),
    services = services,
  )

  private fun DataHeldSummaryRequest.toGetSubjectAccessRequestDataParams() = GetSubjectAccessRequestDataParams(
    prn = this.nomisId,
    crn = this.ndeliusId,
    dateFrom = this.dateFrom,
    dateTo = this.dateTo,
  )

  class DpsServiceWrapper(name: String, private val mockServer: HmppsServiceApiMockServer) :
    DpsService(
      name = name,
      url = "http://localhost:${mockServer.port()}",
    ) {
    fun returnsData(
      params: GetSubjectAccessRequestDataParams,
      responseBody: String,
    ) = this.mockServer.stubGetSubjectAccessRequestData(
      params = params,
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody),
    )

    fun returnsNoData(params: GetSubjectAccessRequestDataParams) = this.mockServer.stubGetSubjectAccessRequestData(
      params = params,
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(204)
        .withHeader("Content-Type", "application/json"),
    )
  }
}
