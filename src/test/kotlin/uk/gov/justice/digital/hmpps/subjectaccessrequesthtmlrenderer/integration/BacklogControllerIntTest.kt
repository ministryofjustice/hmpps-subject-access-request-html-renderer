package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.BacklogController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.BacklogController.SubjectDataHeldRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import java.time.LocalDate

class BacklogControllerIntTest : IntegrationTestBase() {

  private val getSummaryRequest = dataHeldSummaryRequest(
    serviceName = "court-case-service",
    serviceUrl = "http://localhost:${sarDataSourceApi.port()}",
  )

  @Test
  fun `should return data held response services holds data on subject`() {
    hmppsAuthReturnsValidAuthToken()

    returnDataHeldResponse(
      params = getSummaryRequest.toGetSubjectAccessRequestDataParams(),
      responseBody = getServiceResponseBody("court-case-service"),
    )

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(true)
    responseBodySpec.jsonPath("$.serviceName").isEqualTo("court-case-service")
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(true)
  }

  @Test
  fun `should return data not held response when no services hold data on subject`() {
    hmppsAuthReturnsValidAuthToken()

    returnNoDataHeldResponse(
      params = getSummaryRequest.toGetSubjectAccessRequestDataParams(),
    )

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(false)
    responseBodySpec.jsonPath("$.serviceName").isEqualTo("court-case-service")
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(false)
  }

  @Test
  fun `should return data not held response when service returns 209 status code`() {
    hmppsAuthReturnsValidAuthToken()

    returnUnsupportedIdentifierResponse(
      params = getSummaryRequest.toGetSubjectAccessRequestDataParams(),
    )

    val responseBodySpec = sendDataHeldSummaryRequest(getSummaryRequest)

    responseBodySpec.jsonPath("$.nomisId").isEqualTo(getSummaryRequest.nomisId!!)
    responseBodySpec.jsonPath("$.ndeliusId").isEqualTo(getSummaryRequest.ndeliusId!!)
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(false)
    responseBodySpec.jsonPath("$.serviceName").isEqualTo("court-case-service")
    responseBodySpec.jsonPath("$.dataHeld").isEqualTo(false)
  }

  fun sendDataHeldSummaryRequest(
    request: BacklogController.SubjectDataHeldRequest,
  ): WebTestClient.BodyContentSpec = webTestClient.post()
    .uri("/subject-access-request/subject-data-held-summary")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody()

  private fun dataHeldSummaryRequest(serviceName: String, serviceUrl: String) = SubjectDataHeldRequest(
    nomisId = "some-nomis-id",
    ndeliusId = "some-ndelius-id",
    dateFrom = LocalDate.parse("2021-01-01"),
    dateTo = LocalDate.parse("2022-01-01"),
    serviceName = serviceName,
    serviceUrl = serviceUrl,
  )

  private fun SubjectDataHeldRequest.toGetSubjectAccessRequestDataParams() = GetSubjectAccessRequestDataParams(
    prn = this.nomisId,
    crn = this.ndeliusId,
    dateFrom = this.dateFrom,
    dateTo = this.dateTo,
  )

  fun returnDataHeldResponse(
    params: GetSubjectAccessRequestDataParams,
    responseBody: String,
  ) = sarDataSourceApi.stubGetSubjectAccessRequestData(
    params = params,
    responseDefinition = ResponseDefinitionBuilder()
      .withStatus(200)
      .withHeader("Content-Type", "application/json")
      .withBody(responseBody),
  )

  fun returnNoDataHeldResponse(params: GetSubjectAccessRequestDataParams) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = params,
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(204),
    )

  fun returnUnsupportedIdentifierResponse(params: GetSubjectAccessRequestDataParams) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = params,
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(209)
        .withHeader("Content-Type", "application/json"),
    )
}
