package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.CacheService
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

const val SERVICE_RESPONSE_STUBS_DIR = "/integration-tests.service-response-stubs"
const val REFERENCE_HTML_DIR = "/integration-tests/reference-html-stubs"

@ExtendWith(SarDataSourceApiExtension::class)
class RenderTemplateControllerIntTest : IntegrationTestBase() {

  // TODO replace with wiremock when the implementation has been filled in
  @MockitoBean
  private lateinit var cacheService: CacheService

  @Captor
  private lateinit var renderedDataCaptor: ArgumentCaptor<ByteArrayOutputStream>

  @Nested
  inner class RenderTemplateSuccessTest {

    @BeforeEach
    fun setup() {
      // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
      clearAuthorizedClientsCache("sar-html-renderer-client", "AUTH_ADM")
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "hmpps-incentives-api",
      ],
      delimiterString = "|",
    )
    fun `should generate expected html and return status 201 when service data is not cached`(
      serviceName: String,
    ) {
      // Given
      val renderRequest = createRenderRequest(serviceName)
      `no cached data exists for render request`(renderRequest)

      `hmpps-auth returns a valid auth token`()

      val serviceResponseJson = getServiceResponseBody(serviceName)
      `hmpps service returns data for request`(renderRequest, serviceResponseJson)

      // When
      val response = `a render template request is sent`(renderRequest = renderRequest)

      // Then
      `the expected success render response is returned`(response, renderRequest, HttpStatus.SC_CREATED)
      `the expected external service call are made when no data is cached`(renderRequest)
      `the expected HTML is generated`(renderRequest.serviceName!!)
    }
  }

  private fun `no cached data exists for render request`(request: RenderRequest) {
    whenever(cacheService.contains(request.getCacheKey()))
      .thenReturn(false)
  }

  private fun `hmpps-auth returns a valid auth token`() {
    hmppsAuth.stubGrantToken()
  }

  private fun `hmpps service returns data for request`(request: RenderRequest, serviceResponse: String) {
    sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseBody = serviceResponse,
    )
  }

  private fun `the expected success render response is returned`(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
    expectedStatus: Int,
  ) {
    val expectedCacheKey = "${renderRequest.id}_${renderRequest.serviceName}"

    response.expectStatus()
      .isEqualTo(expectedStatus)
      .expectBody()
      .jsonPath("cacheKey").isEqualTo(expectedCacheKey)
  }

  private fun `a render template request is sent`(
    role: String = "ROLE_SAR_DATA_ACCESS",
    renderRequest: RenderRequest,
  ): WebTestClient.ResponseSpec = webTestClient
    .post()
    .uri("/subject-access-request/render")
    .header("Content-Type", "application/json")
    .headers(setAuthorisation(roles = listOf(role)))
    .bodyValue(objectMapper.writeValueAsString(renderRequest))
    .exchange()

  private fun `the expected external service call are made when no data is cached`(renderRequest: RenderRequest) {
    hmppsAuth.verifyGrantTokenIsCalled(times = 1)
    sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(times = 1)

    // TODO replace with wiremock assertions when implementation is filled in
    verify(cacheService, times(1))
      .contains(renderRequest.getCacheKey())

    verify(cacheService, times(1))
      .add(eq(renderRequest.getCacheKey()), capture(renderedDataCaptor))
  }

  fun `the expected HTML is generated`(serviceName: String) {
    val expected = getExpectedHtmlForService(serviceName)

    assertThat(renderedDataCaptor.value).isNotNull
    val actual = String(renderedDataCaptor.value.toByteArray())

    assertThat(actual).isEqualTo(expected)
  }

  fun getServiceResponseBody(serviceName: String): String = getResourceAsString(
    filepath = "$SERVICE_RESPONSE_STUBS_DIR/$serviceName-response.json",
  )

  fun getExpectedHtmlForService(serviceName: String): String = getResourceAsString(
    filepath = "$REFERENCE_HTML_DIR/$serviceName-expected.html",
  )

  fun getResourceAsString(filepath: String): String {
    val jsonBytes = this::class.java.getResourceAsStream(filepath).use { inputStream -> inputStream?.readAllBytes() }
    assertThat(jsonBytes).isNotNull()
    return String(jsonBytes!!)
  }

  fun RenderRequest.toGetSubjectAccessRequestDataParams() = GetSubjectAccessRequestDataParams(
    prn = this.nomisId,
    crn = this.ndeliusId,
    dateFrom = this.dateFrom,
    dateTo = this.dateTo,
  )

  fun createRenderRequest(serviceName: String): RenderRequest = RenderRequest(
    id = UUID.randomUUID(),
    serviceName = serviceName,
    serviceUrl = "http://localhost:${sarDataSourceApi.port()}",
    nomisId = "nomis1234",
    ndeliusId = "ndelius1234",
    dateFrom = LocalDate.of(2020, 1, 1),
    dateTo = LocalDate.of(2021, 1, 1),
  )
}
