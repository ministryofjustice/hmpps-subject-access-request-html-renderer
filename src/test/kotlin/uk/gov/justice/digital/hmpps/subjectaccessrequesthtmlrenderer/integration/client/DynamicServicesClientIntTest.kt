package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.client

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class DynamicServicesClientIntTest : BaseClientIntTest() {

  @Autowired
  private lateinit var dynamicServicesClient: DynamicServicesClient

  private val successResponse = ResponseDefinitionBuilder
    .responseDefinition()
    .withStatus(200)
    .withHeader("Content-Type", "application/json")
    .withBody("""{ "content": ["some value"]}""")

  companion object {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private const val NOMIS_ID = "ABC123"
    private const val NDELIUS_ID = "XYZ123"
    private val DATE_FROM = LocalDate.parse("2024-01-01")
    private val DATE_TO = LocalDate.parse("2025-01-01")

    private fun LocalDate.sarFormat(): String = this.format(dateFormatter)
  }

  @Nested
  inner class OptionalQueryParametersTest {

    @Test
    fun `should not send Nomis ID when value is null`() {
      val request = createRenderRequest(nomisId = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(successResponse, request.expectedQueryParameters())

      val body = dynamicServicesClient.getSubjectAccessRequestData(request)
      assertThat(body).isNotNull

      sarDataSourceApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("crn", equalTo(NDELIUS_ID))
          .withQueryParam("fromDate", equalTo(DATE_FROM.sarFormat()))
          .withQueryParam("toDate", equalTo(DATE_TO.sarFormat()))
          .withoutQueryParam("prn"),
      )
    }

    @Test
    fun `should not send Ndelius ID when value is null`() {
      val request = createRenderRequest(ndeliusId = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(successResponse, request.expectedQueryParameters())

      val body = dynamicServicesClient.getSubjectAccessRequestData(request)
      assertThat(body).isNotNull

      sarDataSourceApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("prn", equalTo(NOMIS_ID))
          .withQueryParam("fromDate", equalTo(DATE_FROM.sarFormat()))
          .withQueryParam("toDate", equalTo(DATE_TO.sarFormat()))
          .withoutQueryParam("crn"),
      )
    }

    @Test
    fun `should not send fromDate when value is null`() {
      val request = createRenderRequest(dateFrom = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(successResponse, request.expectedQueryParameters())

      val body = dynamicServicesClient.getSubjectAccessRequestData(request)
      assertThat(body).isNotNull

      sarDataSourceApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("prn", equalTo(NOMIS_ID))
          .withQueryParam("crn", equalTo(NDELIUS_ID))
          .withQueryParam("toDate", equalTo(DATE_TO.sarFormat()))
          .withoutQueryParam("fromDate"),
      )
    }

    @Test
    fun `should not send toDate when value is null`() {
      val request = createRenderRequest(dateTo = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(successResponse, request.expectedQueryParameters())

      val body = dynamicServicesClient.getSubjectAccessRequestData(request)
      assertThat(body).isNotNull

      sarDataSourceApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("prn", equalTo(NOMIS_ID))
          .withQueryParam("crn", equalTo(NDELIUS_ID))
          .withQueryParam("fromDate", equalTo(DATE_FROM.sarFormat()))
          .withoutQueryParam("toDate"),
      )
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "500",
        "501",
        "502",
        "503",
        "504",
        "505",
      ],
    )
    fun `should retry expected number of times when request fails with 5xx status`(responseStatus: Int) {
      val request = createRenderRequest(nomisId = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(
        responseDefinition = ResponseDefinitionBuilder.responseDefinition().withStatus(responseStatus),
        expectedQueryParams = request.expectedQueryParameters(),
      )

      val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        dynamicServicesClient.getSubjectAccessRequestData(request)
      }

      assertThat(actual.message).startsWith("request failed and max retry attempts (2) exhausted")
      assertThat(actual.subjectAccessRequestId).isEqualTo(request.id)
      assertThat(actual.subjectAccessRequestId).isEqualTo(request.id)

      sarDataSourceApi.verify(
        3,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("crn", equalTo(NDELIUS_ID))
          .withQueryParam("fromDate", equalTo(DATE_FROM.sarFormat()))
          .withQueryParam("toDate", equalTo(DATE_TO.sarFormat()))
          .withoutQueryParam("prn"),
      )
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "400",
        "401",
        "402",
        "403",
        "404",
      ],
    )
    fun `should not retry when request fails with 4xx status`(responseStatus: Int) {
      val request = createRenderRequest(nomisId = null)

      hmppsAuth.stubGrantToken()
      sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(
        responseDefinition = ResponseDefinitionBuilder.responseDefinition().withStatus(responseStatus),
        expectedQueryParams = request.expectedQueryParameters(),
      )

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        dynamicServicesClient.getSubjectAccessRequestData(request)
      }

      assertThat(actual.message)
        .startsWith("subjectAccessRequest failed with non-retryable error: response status: $responseStatus not retryable")
      assertThat(actual.subjectAccessRequestId).isEqualTo(request.id)

      sarDataSourceApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/subject-access-request"))
          .withQueryParam("crn", equalTo(NDELIUS_ID))
          .withQueryParam("fromDate", equalTo(DATE_FROM.sarFormat()))
          .withQueryParam("toDate", equalTo(DATE_TO.sarFormat()))
          .withoutQueryParam("prn"),
      )
    }

    private fun createRenderRequest(
      nomisId: String? = NOMIS_ID,
      ndeliusId: String? = NDELIUS_ID,
      dateFrom: LocalDate? = DATE_FROM,
      dateTo: LocalDate? = DATE_TO,
    ) = RenderRequest(
      id = UUID.randomUUID(),
      nomisId = nomisId,
      ndeliusId = ndeliusId,
      dateFrom = dateFrom,
      dateTo = dateTo,
      serviceUrl = "http://localhost:8092",
      serviceName = "A-Service",
    )

    private fun RenderRequest.expectedQueryParameters(): Map<String, StringValuePattern> {
      val map = mutableMapOf<String, StringValuePattern>()
      this.nomisId?.let { map["prn"] to equalTo(it) }
      this.ndeliusId?.let { map["crn"] to equalTo(it) }
      this.dateFrom?.let { map["fromDate"] to equalTo(it.sarFormat()) }
      this.dateTo?.let { map["toDate"] to equalTo(it.sarFormat()) }
      return map
    }
  }
}
