package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi

const val SERVICE_RESPONSE_STUBS_DIR = "/integration-tests.service-response-stubs"
const val REFERENCE_HTML_DIR = "/integration-tests/reference-html-stubs"

@ExtendWith(SarDataSourceApiExtension::class)
class RenderTemplateControllerIntTest : IntegrationTestBase() {

  @Nested
  inner class RenderTemplateSuccessTest {

    @BeforeEach
    fun setup() {
      // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
      clearAuthorizedClientsCache("sar-html-renderer-client", "AUTH_ADM")
      clearS3Bucket()
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "hmpps-incentives-api",
      ],
      delimiterString = "|",
    )
    fun `should generate expected html and return status 201 when service data is not cached`(serviceName: String) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName)
      assertServiceDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceName)

      // When
      val response = sendRenderTemplateRequest(renderRequest = renderRequest)

      // Then
      assertRenderTemplateSuccessResponse(response, renderRequest, HttpStatus.SC_CREATED)
      assertExpectedServiceCallsAreMade(renderRequest)

      val uploadedFile = getHtmlFileFromS3(renderRequest.documentKey())
      assertThat(uploadedFile).isNotNull()
      assertThat(uploadedFile).isNotEmpty()

      val expectedHtml = getExpectedHtmlForService(serviceName)
      assertThat("$uploadedFile >>> WIBBLE >>>>").isEqualTo(expectedHtml)
    }
  }

  private fun hmppsServiceReturnsDataForRequest(request: RenderRequest, serviceName: String) {
    sarDataSourceApi.stubGetSubjectAccessRequestDataSuccess(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseBody = getServiceResponseBody(serviceName),
    )
  }

  private fun assertRenderTemplateSuccessResponse(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
    expectedStatus: Int,
  ) {
    val expectedCacheKey = "${renderRequest.id}_${renderRequest.serviceName}"

    response.expectStatus()
      .isEqualTo(expectedStatus)
      .expectBody()
      .jsonPath("documentKey").isEqualTo(expectedCacheKey)
  }

  private fun sendRenderTemplateRequest(
    role: String = "ROLE_SAR_DATA_ACCESS",
    renderRequest: RenderRequest,
  ): WebTestClient.ResponseSpec = webTestClient
    .post()
    .uri("/subject-access-request/render")
    .header("Content-Type", "application/json")
    .headers(setAuthorisation(roles = listOf(role)))
    .bodyValue(objectMapper.writeValueAsString(renderRequest))
    .exchange()

  private fun assertExpectedServiceCallsAreMade(renderRequest: RenderRequest) {
    hmppsAuth.verifyGrantTokenIsCalled(times = 1)
    sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(times = 1)
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
}
