package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi

const val SERVICE_RESPONSE_STUBS_DIR = "/integration-tests.service-response-stubs"
const val REFERENCE_HTML_DIR = "/integration-tests/reference-html-stubs"

@ExtendWith(SarDataSourceApiExtension::class)
class RenderTemplateControllerIntTest : IntegrationTestBase() {

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
    clearAuthorizedClientsCache("sar-html-renderer-client", "anonymousUser")
    s3TestUtil.clearBucket()
  }

  @AfterEach
  fun tearDown() {
    s3TestUtil.clearBucket()
  }

  @Nested
  inner class RenderTemplateSuccessTest {

    @ParameterizedTest
    @CsvSource(
      value = [
        "hmpps-incentives-api",
        "hmpps-book-secure-move-api",
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
      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      assertUploadedHtmlMatchesExpected(
        renderRequest = renderRequest,
        expectedHtmlFilename = serviceName,
      )
    }

    @Test
    fun `should not store html and return status 204 when service data exists in cache`() {
      // Given
      val serviceName = "hmpps-book-secure-move-api"
      val renderRequest = newRenderRequestFor(serviceName)
      addServiceDocumentToBucket(renderRequest)

      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceName)

      // When
      val response = sendRenderTemplateRequest(renderRequest = renderRequest)

      // Then
      assertRenderTemplateSuccessResponseNoContent(response)
      hmppsAuth.verifyGrantTokenIsNeverCalled()
      sarDataSourceApi.verifyGetSubjectAccessRequestDataNeverCalled()

      assertUploadedHtmlMatchesExpected(
        renderRequest = renderRequest,
        expectedHtmlFilename = serviceName,
      )
    }
  }

  @Nested
  inner class RenderTemplateNoDataSuccessTest {

    @ParameterizedTest
    @CsvSource(
      value = [
        "hmpps-incentives-api",
        "hmpps-book-secure-move-api",
      ],
      delimiterString = "|",
    )
    fun `should store empty html document service data is empty`(serviceName: String) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName)
      assertServiceDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsNoDataForRequest(renderRequest)

      // When
      val response = sendRenderTemplateRequest(renderRequest = renderRequest)

      // Then
      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      assertUploadedHtmlMatchesExpected(
        renderRequest = renderRequest,
        expectedHtmlFilename = "$serviceName-no-data",
      )
    }
  }

  private fun assertUploadedHtmlMatchesExpected(renderRequest: RenderRequest, expectedHtmlFilename: String) {
    val uploadedFile = s3TestUtil.getFile(renderRequest.documentKey())
    assertThat(uploadedFile).isNotNull()
    assertThat(uploadedFile).isNotEmpty()

    val expectedHtml = getExpectedHtmlString(expectedHtmlFilename)
    assertThat(uploadedFile).isEqualTo(expectedHtml)
  }

  private fun hmppsServiceReturnsDataForRequest(request: RenderRequest, serviceName: String) = sarDataSourceApi
    .stubGetSubjectAccessRequestDataSuccess(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseBody = getServiceResponseBody(serviceName),
    )

  private fun hmppsServiceReturnsNoDataForRequest(request: RenderRequest) = sarDataSourceApi
    .stubGetSubjectAccessRequestDataEmpty(request.toGetSubjectAccessRequestDataParams())

  private fun assertRenderTemplateSuccessResponse(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
  ) = response.expectStatus()
    .isEqualTo(HttpStatus.CREATED)
    .expectBody()
    .jsonPath("documentKey").isEqualTo("${renderRequest.id}/${renderRequest.serviceName}.html")

  private fun assertRenderTemplateSuccessResponseNoContent(response: WebTestClient.ResponseSpec) = response
    .expectStatus()
    .isEqualTo(HttpStatus.NO_CONTENT)

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
}
