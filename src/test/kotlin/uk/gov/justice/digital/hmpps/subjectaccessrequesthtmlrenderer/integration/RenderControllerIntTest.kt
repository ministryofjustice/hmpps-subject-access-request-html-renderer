package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE_HTML_CACHED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_ERRORED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_RECEIVED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository
import java.time.format.DateTimeFormatter

const val SERVICE_RESPONSE_STUBS_DIR = "/integration-tests.service-response-stubs"
const val REFERENCE_HTML_DIR = "/integration-tests/reference-html-stubs"

@ExtendWith(SarDataSourceApiExtension::class)
class RenderControllerIntTest : IntegrationTestBase() {

  @MockitoBean
  private lateinit var userDetailsRepository: UserDetailsRepository

  @MockitoBean
  private lateinit var prisonDetailsRepository: PrisonDetailsRepository

  @MockitoBean
  private lateinit var locationDetailsRepository: LocationDetailsRepository

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
    clearAuthorizedClientsCache("sar-html-renderer-client", "anonymousUser")
    s3TestUtil.clearBucket()

    whenever(userDetailsRepository.findByUsername(any())).doAnswer {
      (UserDetail(it.arguments[0] as String, "Homer Simpson"))
    }

    whenever(prisonDetailsRepository.findByPrisonId(any())).doAnswer {
      val id = it.arguments[0] as String
      PrisonDetail(id, "HMPPS Mordor")
    }

    whenever(locationDetailsRepository.findByDpsId(any())).doAnswer {
      val dpsId = it.arguments[0] as String
      LocationDetail(dpsId, 666, "Hogwarts")
    }

    whenever(locationDetailsRepository.findByNomisId(any())).doAnswer {
      val nomisId = it.arguments[0] as Int
      LocationDetail("666", nomisId, "Hogwarts")
    }
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
        "keyworker-api | Keyworker",
        "offender-case-notes | Sensitive Case Notes",
        "court-case-service | Prepare a Case for Sentence",
        "hmpps-restricted-patients-api | Restricted Patients",
        "hmpps-accredited-programmes-api | Accredited Programmes",
        "hmpps-complexity-of-need | Complexity Of Need",
        "offender-management-allocation-manager | Manage Prison Offender Manager Cases",
        "hmpps-book-secure-move-api | Book a Secure Move",
        "hmpps-education-and-work-plan-api | Personal Learning Plan",
        "hmpps-non-associations-api | Non-associations",
        "hmpps-incentives-api | Incentives",
        "hmpps-manage-adjudications-api | Manage Adjudications",
        "hmpps-offender-categorisation-api | Categorisation Tool",
        "make-recall-decision-api | Consider a Recall",
        "hmpps-hdc-api | Home Detention Curfew",
        "create-and-vary-a-licence-api | Create and Vary a Licence",
        "hmpps-uof-data-api | Use of Force",
        "hmpps-activities-management-api | Manage Activities and Appointments",
        "hmpps-interventions-service | Refer and Monitor an Intervention",
        "hmpps-resettlement-passport-api | Prepare Someone for Release",
        "hmpps-approved-premises-api | Approved Premises",
        "hmpps-education-employment-api | Education Employment",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should generate expected html and return status 201 when service data is not cached`(
      serviceName: String,
      serviceLabel: String,
    ) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName, serviceLabel)
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
        expectedHtml = getExpectedHtmlString(serviceName),
      )

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(SERVICE_DATA_RETURNED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(REQUEST_COMPLETE, eventProperties(renderRequest)),
      )
    }

    @Test
    fun `should not store html and return status 204 when service data exists in cache`() {
      // Given
      val serviceName = "hmpps-book-secure-move-api"
      val serviceLabel = "Book a Secure Move"
      val renderRequest = newRenderRequestFor(serviceName, serviceLabel)
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
        expectedHtml = getExpectedHtmlString(serviceName),
      )

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(REQUEST_COMPLETE_HTML_CACHED, eventProperties(renderRequest)),
      )
    }
  }

  @Nested
  inner class RenderTemplateNoDataSuccessTest {

    @ParameterizedTest
    @CsvSource(
      value = [
        "keyworker-api | Keyworker",
        "offender-case-notes | Sensitive Case Notes",
        "court-case-service | Prepare a Case for Sentence",
        "hmpps-restricted-patients-api | Restricted Patients",
        "hmpps-accredited-programmes-api | Accredited Programmes",
        "hmpps-complexity-of-need | Complexity Of Need",
        "offender-management-allocation-manager | Manage Prison Offender Manager Cases",
        "hmpps-book-secure-move-api | Book a Secure Move",
        "hmpps-education-and-work-plan-api | Personal Learning Plan",
        "hmpps-non-associations-api | Non-associations",
        "hmpps-incentives-api | Incentives",
        "hmpps-manage-adjudications-api | Manage Adjudications",
        "hmpps-offender-categorisation-api | Categorisation Tool",
        "make-recall-decision-api | Consider a Recall",
        "hmpps-hdc-api | Home Detention Curfew",
        "create-and-vary-a-licence-api | Create and Vary a Licence",
        "hmpps-uof-data-api | Use of Force",
        "hmpps-activities-management-api | Manage Activities and Appointments",
        "hmpps-interventions-service | Refer and Monitor an Intervention",
        "hmpps-resettlement-passport-api | Prepare Someone for Release",
        "hmpps-approved-premises-api | Approved Premises",
        "hmpps-education-employment-api | Education Employment",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should store empty html document when service data is empty`(serviceName: String, serviceLabel: String) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName, serviceLabel)
      assertServiceDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsNoDataForRequest(renderRequest)

      // When
      val response = sendRenderTemplateRequest(renderRequest = renderRequest)

      // Then
      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      val expectedHtml = getExpectedHtmlString("no-data").replace("{{serviceLabel}}", serviceLabel)

      assertUploadedHtmlMatchesExpected(
        renderRequest = renderRequest,
        expectedHtml = expectedHtml,
      )
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "keyworker-api | Keyworker",
        "offender-case-notes | Sensitive Case Notes",
        "court-case-service | Prepare a Case for Sentence",
        "hmpps-restricted-patients-api | Restricted Patients",
        "hmpps-accredited-programmes-api | Accredited Programmes",
        "hmpps-complexity-of-need | Complexity Of Need",
        "offender-management-allocation-manager | Manage Prison Offender Manager Cases",
        "hmpps-book-secure-move-api | Book a Secure Move",
        "hmpps-education-and-work-plan-api | Personal Learning Plan",
        "hmpps-non-associations-api | Non-associations",
        "hmpps-incentives-api | Incentives",
        "hmpps-manage-adjudications-api | Manage Adjudications",
        "hmpps-offender-categorisation-api | Categorisation Tool",
        "make-recall-decision-api | Consider a Recall",
        "hmpps-hdc-api | Home Detention Curfew",
        "create-and-vary-a-licence-api | Create and Vary a Licence",
        "hmpps-uof-data-api | Use of Force",
        "hmpps-activities-management-api | Manage Activities and Appointments",
        "hmpps-interventions-service | Refer and Monitor an Intervention",
        "hmpps-resettlement-passport-api | Prepare Someone for Release",
        "hmpps-approved-premises-api | Approved Premises",
        "hmpps-education-employment-api | Education Employment",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should store empty html document when service returns status 209`(serviceName: String, serviceLabel: String) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName, serviceLabel)
      assertServiceDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsIdentifierNotSupportedForRequest(renderRequest)

      // When
      val response = sendRenderTemplateRequest(renderRequest = renderRequest)

      // Then
      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      val expectedHtml = getExpectedHtmlString("no-data").replace("{{serviceLabel}}", serviceLabel)
      assertUploadedHtmlMatchesExpected(
        renderRequest = renderRequest,
        expectedHtml = expectedHtml,
      )
    }
  }

  @Nested
  inner class RenderTemplateErrorsTest {

    @ParameterizedTest
    @CsvSource(
      value = [
        "keyworker-api | Keyworker",
        "offender-case-notes | Sensitive Case Notes",
        "court-case-service | Prepare a Case for Sentence",
        "hmpps-restricted-patients-api | Restricted Patients",
        "hmpps-accredited-programmes-api | Accredited Programmes",
        "hmpps-complexity-of-need | Complexity Of Need",
        "offender-management-allocation-manager | Manage Prison Offender Manager Cases",
        "hmpps-book-secure-move-api | Book a Secure Move",
        "hmpps-education-and-work-plan-api | Personal Learning Plan",
        "hmpps-non-associations-api | Non-associations",
        "hmpps-incentives-api | Incentives",
        "hmpps-manage-adjudications-api | Manage Adjudications",
        "hmpps-offender-categorisation-api | Categorisation Tool",
        "make-recall-decision-api | Consider a Recall",
        "hmpps-hdc-api | Home Detention Curfew",
        "create-and-vary-a-licence-api | Create and Vary a Licence",
        "hmpps-uof-data-api | Use of Force",
        "hmpps-activities-management-api | Manage Activities and Appointments",
        "hmpps-interventions-service | Refer and Monitor an Intervention",
        "hmpps-resettlement-passport-api | Prepare Someone for Release",
        "hmpps-approved-premises-api | Approved Premises",
        "hmpps-education-employment-api | Education Employment",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should return internal server error when hmpps service unavailable after retries maxed`(
      serviceName: String,
      serviceLabel: String,
    ) {
      // Given
      val renderRequest = newRenderRequestFor(serviceName, serviceLabel)
      assertServiceDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsErrorForRequest(renderRequest, HttpStatus.INTERNAL_SERVER_ERROR)

      // When
      sendRenderTemplateRequest(renderRequest = renderRequest)
        .expectStatus().isEqualTo(500)

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA_RETRY, getDataRetryEventProperties(renderRequest, 0, 500)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA_RETRY, getDataRetryEventProperties(renderRequest, 1, 500)),
        ExpectedTelemetryEvent(REQUEST_ERRORED, requestErroredEventProperties(renderRequest, 500)),
      )

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(3)
    }
  }

  private fun assertUploadedHtmlMatchesExpected(renderRequest: RenderRequest, expectedHtml: String) {
    val uploadedFile = s3TestUtil.getFile(renderRequest.documentKey())
    assertThat(uploadedFile).isNotNull()
    assertThat(uploadedFile).isNotEmpty()

    assertThat(uploadedFile).isEqualTo(expectedHtml)
  }

  private fun hmppsServiceReturnsDataForRequest(request: RenderRequest, serviceName: String) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(getServiceResponseBody(serviceName)),
    )

  private fun hmppsServiceReturnsNoDataForRequest(request: RenderRequest) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(204)
        .withHeader("Content-Type", "application/json"),
    )

  private fun hmppsServiceReturnsErrorForRequest(request: RenderRequest, status: HttpStatus) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(status.value())
        .withHeader("Content-Type", "application/json")
        .withBody("""{ "error": "some error" "}"""),
    )

  private fun hmppsServiceReturnsIdentifierNotSupportedForRequest(request: RenderRequest) = sarDataSourceApi
    .stubGetSubjectAccessRequestIdentifierNotSupported(request.toGetSubjectAccessRequestDataParams())

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

  private fun getDataRetryEventProperties(
    renderRequest: RenderRequest,
    attempt: Int,
    responseStatus: Int,
  ) = eventProperties(
    renderRequest,
    "uri" to "http://localhost:${sarDataSourceApi.port()}",
    "error" to "GET ${getSarRequestUrl(renderRequest)}, status: $responseStatus",
    "retryAttempts" to attempt.toString(),
    "backOff" to webClientConfiguration.backOff,
    "maxRetries" to webClientConfiguration.maxRetries.toString(),
  )

  private fun requestErroredEventProperties(renderRequest: RenderRequest, status: Int) = eventProperties(
    renderRequest,
    "uri" to "http://localhost:${sarDataSourceApi.port()}",
    "errorMessage" to retryExhaustedErrorMessage(renderRequest, status),
  )

  private fun retryExhaustedErrorMessage(renderRequest: RenderRequest, status: Int): String = "request failed and max " +
    "retry attempts (${webClientConfiguration.maxRetries}) exhausted, cause=GET ${getSarRequestUrl(renderRequest)}, " +
    "status: $status, id=${renderRequest.id}, serviceName=${renderRequest.serviceName}, uri=${renderRequest.serviceUrl}"

  private fun getSarRequestUrl(
    request: RenderRequest,
  ): String {
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val sb = StringBuilder("http://localhost:${sarDataSourceApi.port()}/subject-access-request")
    request.nomisId?.let { sb.append("?prn=${request.nomisId}") } ?: sb.append("?crn=${request.ndeliusId}")
    request.dateFrom?.let { sb.append("&fromDate=${format.format(request.dateFrom)}") }
    request.dateTo?.let { sb.append("&toDate=${format.format(request.dateTo)}") }
    return sb.toString()
  }
}
