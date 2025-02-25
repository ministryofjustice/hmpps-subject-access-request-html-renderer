package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

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

@ExtendWith(SarDataSourceApiExtension::class)
class RenderTemplateControllerIntTest : IntegrationTestBase() {

  // TODO replace with wiremock when the implementation has been filled in
  @MockitoBean
  private lateinit var cacheService: CacheService

  @Captor
  private lateinit var renderedDataCaptor: ArgumentCaptor<ByteArrayOutputStream>

  private val renderRequest = createRenderRequest(sarDataSourceApi.port())
  private val expectedCacheKey = "${renderRequest.id}_${renderRequest.serviceName}"

  @Nested
  inner class RenderTemplateAuthTest {

    @BeforeEach
    fun setup() {
      // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
      clearAuthorizedClientsCache("sar-html-renderer-client", "AUTH_ADM")
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "ROLE_SAR_USER_ACCESS | 201",
        "ROLE_SAR_DATA_ACCESS | 201",
        "ROLE_SAR_SUPPORT     | 201",
      ],
      delimiterString = "|",
    )
    fun `should return resource created when token with required role is provided`(
      authRole: String,
      expectedStatus: Int,
    ) {
      // Given
      `no cached data exists for render request`(renderRequest)
      `hmpps-auth returns a valid auth token`()
      `hmpps service x returns data for request`(renderRequest, SAR_RESPONSE)

      // When
      val response = `a render template request is sent`(authRole, renderRequest)

      // Then
      `the expected success render response is returned`(response, expectedStatus, expectedCacheKey)
      `the expected external service call are made when no data is cached`()
    }
  }

  private fun `no cached data exists for render request`(request: RenderRequest) {
    whenever(cacheService.contains(request.getCacheKey()))
      .thenReturn(false)
  }

  private fun `hmpps-auth returns a valid auth token`() {
    hmppsAuth.stubGrantToken()
  }

  private fun `hmpps service x returns data for request`(request: RenderRequest, serviceResponse: String) {
    sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseBody = serviceResponse,
    )
  }

  private fun `the expected success render response is returned`(
    response: WebTestClient.ResponseSpec,
    expectedStatus: Int,
    expectedCacheKey: String,
  ) {
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

  private fun `the expected external service call are made when no data is cached`() {
    hmppsAuth.verifyGrantTokenIsCalled(times = 1)
    sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(times = 1)

    // TODO replace with wiremock assertions when implementation is filled in
    verify(cacheService, times(1))
      .contains(renderRequest.getCacheKey())

    verify(cacheService, times(1))
      .add(eq(expectedCacheKey), capture(renderedDataCaptor))

    assertThat(renderedDataCaptor.value).isNotNull
    assertThat(String(renderedDataCaptor.value.toByteArray())).isEqualTo("Hello World!!")
  }

  fun RenderRequest.toGetSubjectAccessRequestDataParams() = GetSubjectAccessRequestDataParams(
    prn = this.nomisId,
    crn = this.ndeliusId,
    dateFrom = this.dateFrom,
    dateTo = this.dateTo,
  )
}
