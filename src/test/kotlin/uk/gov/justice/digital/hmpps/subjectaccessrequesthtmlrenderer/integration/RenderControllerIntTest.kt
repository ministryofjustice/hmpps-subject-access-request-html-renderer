package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.S3TestUtil.AttachmentMetadata
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_ERRORED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_RECEIVED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequestEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.TemplateVersionService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

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

  @Autowired
  private lateinit var templateVersionService: TemplateVersionService

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
        "keyworker-api",
        "offender-case-notes",
        "court-case-service",
        "hmpps-restricted-patients-api",
        "hmpps-accredited-programmes-api",
        "hmpps-complexity-of-need",
        "offender-management-allocation-manager",
        "hmpps-book-secure-move-api",
        "hmpps-education-and-work-plan-api",
        "hmpps-non-associations-api",
        "hmpps-incentives-api",
        "hmpps-manage-adjudications-api",
        "hmpps-offender-categorisation-api",
        "make-recall-decision-api",
        "hmpps-hdc-api",
        "create-and-vary-a-licence-api",
        "hmpps-uof-data-api",
        "hmpps-activities-management-api",
        "hmpps-interventions-service",
        "hmpps-resettlement-passport-api",
        "hmpps-approved-premises-api",
        "hmpps-education-employment-api",
        "hmpps-health-and-medication-api",
        "hmpps-managing-prisoner-apps-api",
        "hmpps-support-additional-needs-api",
        "G1",
        "G2",
        "G3",
        "wiremock-test-service-api",
      ],
      delimiterString = "|",
    )
    fun `should generate expected html and return status 201 service data successfully retrieved and rendered`(
      serviceName: String,
    ) {
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceName)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()

      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(serviceName))
      assertStoredTemplateVersionMatchesExpected(renderRequest, "vlegacy")

      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)

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

      sarDataSourceApi.verifyGetTemplateNeverCalled()
    }

    @Test
    fun `should store html with the correct templateVersion metadata when no data is held`() {
      val serviceName = "launchpad-auth"
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertNoTemplateVersionExistsForService(serviceConfiguration)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceName)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(serviceName))
      assertNoTemplateVersionExistsForService(serviceConfiguration)
      assertStoredTemplateVersionMatchesExpected(renderRequest, "vlegacy")

      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)

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

      sarDataSourceApi.verifyGetTemplateNeverCalled()
    }

    @Test
    fun `should overwrite existing rendered HTML stored in S3 bucket`() {
      val serviceName = "hmpps-book-secure-move-api"
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      val existingHtmlContent = "<html><body><h1>Existing ${serviceConfiguration.label} V0</body></html>"
      val s3Key = "${renderRequest.id}/${serviceConfiguration.serviceName}.html"

      // Add some existing data to the bucket,
      addContentToBucket(key = s3Key, content = existingHtmlContent)
      assertBucketContentMatchesExpected(s3Key, existingHtmlContent)

      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceName)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      assertBucketContentMatchesExpected(s3Key, getExpectedHtmlString(serviceName))

      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)

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
    fun `should download and store attachments in S3 and return status 201`() {
      val serviceName = "create-and-vary-a-licence-api"
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 1, "doc.pdf")
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 2, "map.jpg")
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, "$serviceName-attachments")
      hmppsServiceReturnsAttachmentForRequest("doc.pdf", "application/pdf")
      hmppsServiceReturnsAttachmentForRequest("map.jpg", "image/jpeg")

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetAttachmentCalled("doc.pdf")
      sarDataSourceApi.verifyGetAttachmentCalled("map.jpg")

      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(serviceName))
      assertUploadedAttachmentMatchesExpected(
        renderRequest,
        getResourceAsByteArray("/attachments/doc.pdf"),
        AttachmentMetadata(
          contentType = "application/pdf",
          filesize = 273289,
          filename = "doc.pdf",
          attachmentNumber = "1",
          name = "Test PDF attachment",
        ),
      )
      assertUploadedAttachmentMatchesExpected(
        renderRequest,
        getResourceAsByteArray("/attachments/map.jpg"),
        AttachmentMetadata(
          contentType = "image/jpeg",
          filesize = 683919,
          filename = "map.jpg",
          attachmentNumber = "2",
          name = "Test Image attachment",
        ),
      )

      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(SERVICE_DATA_RETURNED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_COMPLETE, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_ATTACHMENT_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_COMPLETE, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_ATTACHMENT_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(REQUEST_COMPLETE, eventProperties(renderRequest)),
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
        "hmpps-education-employment-api | Work Readiness",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "hmpps-managing-prisoner-apps-api | Managing Prisoner Applications",
        "hmpps-support-additional-needs-api | Support for Additional Needs",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should store empty json and html document when service data is empty`(
      serviceName: String,
      serviceLabel: String,
    ) {
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsNoDataForRequest(renderRequest)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()

      val expectedHtml = getExpectedHtmlString("no-data").replace("{{serviceLabel}}", serviceLabel)
      assertUploadedHtmlMatchesExpected(renderRequest, expectedHtml)
      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)
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
        "hmpps-education-employment-api | Work Readiness",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "hmpps-managing-prisoner-apps-api | Managing Prisoner Applications",
        "hmpps-support-additional-needs-api | Support for Additional Needs",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should store empty html document when service data exists and is empty`(
      serviceName: String,
      serviceLabel: String,
    ) {
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsNoDataForRequest(renderRequest)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(1)

      val expectedHtml = getExpectedHtmlString("no-data").replace("{{serviceLabel}}", serviceLabel)
      assertUploadedHtmlMatchesExpected(renderRequest, expectedHtml)
      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)
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
        "hmpps-education-employment-api | Work Readiness",
        "launchpad-auth | Launchpad",
        "hmpps-health-and-medication-api | Health and Medication",
        "hmpps-managing-prisoner-apps-api | Managing Prisoner Applications",
        "hmpps-support-additional-needs-api | Support for Additional Needs",
        "G1 | G1",
        "G2 | G2",
        "G3 | G3",
      ],
      delimiterString = "|",
    )
    fun `should store empty json and html document when service returns status 209`(
      serviceName: String,
      serviceLabel: String,
    ) {
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsIdentifierNotSupportedForRequest(renderRequest)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()

      val expectedHtml = getExpectedHtmlString("no-data").replace("{{serviceLabel}}", serviceLabel)

      assertUploadedHtmlMatchesExpected(renderRequest, expectedHtml)
      assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(renderRequest)
    }
  }

  @Nested
  inner class RenderTemplateErrorsTest {

    @ParameterizedTest
    @CsvSource(
      value = [
        "keyworker-api",
        "offender-case-notes",
        "court-case-service",
        "hmpps-restricted-patients-api",
        "hmpps-accredited-programmes-api",
        "hmpps-complexity-of-need",
        "offender-management-allocation-manager",
        "hmpps-book-secure-move-api",
        "hmpps-education-and-work-plan-api",
        "hmpps-non-associations-api",
        "hmpps-incentives-api",
        "hmpps-manage-adjudications-api",
        "hmpps-offender-categorisation-api",
        "make-recall-decision-api",
        "hmpps-hdc-api",
        "create-and-vary-a-licence-api",
        "hmpps-uof-data-api",
        "hmpps-activities-management-api",
        "hmpps-interventions-service",
        "hmpps-resettlement-passport-api",
        "hmpps-approved-premises-api",
        "hmpps-education-employment-api",
        "launchpad-auth",
        "hmpps-health-and-medication-api",
        "hmpps-managing-prisoner-apps-api",
        "G1",
        "G2",
        "G3",
      ],
      delimiterString = "|",
    )
    fun `should return internal server error when hmpps service unavailable after retries maxed`(
      serviceName: String,
    ) {
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsErrorForRequest(renderRequest, HttpStatus.INTERNAL_SERVER_ERROR)

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA_RETRY, getDataRetryEventProperties(renderRequest, 0, 500)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA_RETRY, getDataRetryEventProperties(renderRequest, 1, 500)),
        ExpectedTelemetryEvent(REQUEST_ERRORED, dataRequestErroredEventProperties(renderRequest, 500)),
      )

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled(3)
    }

    @Test
    fun `should return internal server error when download attachment unavailable after retries maxed`() {
      val serviceName = "create-and-vary-a-licence-api"
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 1, "doc.pdf")
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 2, "map.jpg")
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, "$serviceName-attachments")
      hmppsServiceReturnsErrorForAttachmentRequest("doc.pdf", "application/pdf", HttpStatus.INTERNAL_SERVER_ERROR)

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)

      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(serviceName))

      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(SERVICE_DATA_RETURNED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_RETRY, getAttachmentRetryEventProperties(renderRequest, "doc.pdf", 0)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_RETRY, getAttachmentRetryEventProperties(renderRequest, "doc.pdf", 1)),
        ExpectedTelemetryEvent(REQUEST_ERRORED, attachmentRequestErroredEventProperties(renderRequest, "doc.pdf")),
      )

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetAttachmentCalled("doc.pdf", 3)
      sarDataSourceApi.verifyGetAttachmentNeverCalled("map.jpg")
    }

    @Test
    fun `should return internal server error when download attachment incorrect size after retries maxed`() {
      val serviceName = "create-and-vary-a-licence-api"
      val serviceConfiguration = getServiceConfiguration(serviceName)
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfiguration)

      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 1, "doc.pdf")
      assertServiceAttachmentDoesNotAlreadyExist(renderRequest, 2, "map.jpg")
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, "$serviceName-attachments")
      hmppsServiceReturnsAttachmentForRequest(
        "doc.pdf",
        "application/pdf",
        getResourceAsByteArray("/attachments/map.jpg"),
      )

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)

      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(serviceName))

      val retryErrorMessage =
        "Attachment GET http://localhost:${sarDataSourceApi.port()}/attachments/doc.pdf expected filesize 273289 does not match retrieved data size 683919"
      assertTelemetryEvents(
        ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(SERVICE_DATA_RETURNED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(RENDER_TEMPLATE_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(STORE_RENDERED_HTML_COMPLETED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(GET_ATTACHMENT_STARTED, eventProperties(renderRequest)),
        ExpectedTelemetryEvent(
          GET_ATTACHMENT_RETRY,
          getAttachmentRetryEventProperties(renderRequest, "doc.pdf", retryErrorMessage, 0),
        ),
        ExpectedTelemetryEvent(
          GET_ATTACHMENT_RETRY,
          getAttachmentRetryEventProperties(renderRequest, "doc.pdf", retryErrorMessage, 1),
        ),
        ExpectedTelemetryEvent(
          REQUEST_ERRORED,
          requestErroredEventProperties(
            renderRequest,
            "http://localhost:${sarDataSourceApi.port()}/attachments/doc.pdf",
            retryErrorMessage,
          ),
        ),
      )

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetAttachmentCalled("doc.pdf", 3)
      sarDataSourceApi.verifyGetAttachmentNeverCalled("map.jpg")
    }
  }

  @Nested
  inner class ValidationErrors {

    @Test
    fun `should return status 400 error when id is null`() {
      assertBadRequestWithExpectedMessage(
        request = RenderRequestEntity(id = null),
        expectedMessage = "request.id was null",
      )
    }

    @Test
    fun `should return status 400 error when serviceConfigurationId is null`() {
      val request = RenderRequestEntity(
        id = UUID.randomUUID(),
        serviceConfigurationId = null,
      )
      assertBadRequestWithExpectedMessage(
        request = request,
        expectedMessage = "request.serviceConfigurationId was null",
      )
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        " | ",
        " '' | '' ",
      ],
      delimiterString = "|",
    )
    fun `should return status 400 error when nomisId and ndeliusId null or empty`(
      nomisId: String?,
      ndeliusId: String?,
    ) {
      val request = RenderRequestEntity(
        id = UUID.randomUUID(),
        serviceConfigurationId = UUID.randomUUID(),
        nomisId = nomisId,
        ndeliusId = ndeliusId,
      )

      assertBadRequestWithExpectedMessage(
        request = request,
        expectedMessage = "request.nomisId and request.ndeliusId was null or empty",
      )
    }

    @Test
    fun `should be successful when dateFrom is null`() {
      val serviceConfig = getServiceConfiguration("keyworker-api")

      val renderRequestEntity = RenderRequestEntity(
        id = UUID.randomUUID(),
        nomisId = "nomis1234",
        ndeliusId = null,
        dateFrom = null,
        dateTo = LocalDate.of(2021, 1, 1),
        serviceConfigurationId = serviceConfig.id,
        sarCaseReferenceNumber = "AAA",
      )
      val renderRequest = RenderRequest(renderRequestEntity, serviceConfig)

      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest, serviceConfig.serviceName)

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
    }

    @Test
    fun `should return status 400 error when dateTo is null`() {
      val request = RenderRequestEntity(
        id = UUID.randomUUID(),
        serviceConfigurationId = UUID.randomUUID(),
        nomisId = "A",
        dateFrom = LocalDate.now(),
      )
      assertBadRequestWithExpectedMessage(
        request = request,
        expectedMessage = "request.dateTo was null",
      )
    }

    @Test
    fun `should return status 400 error when dateTo is before dateFrom`() {
      val request = RenderRequestEntity(
        id = UUID.randomUUID(),
        serviceConfigurationId = UUID.randomUUID(),
        nomisId = "A",
        dateFrom = LocalDate.now(),
        dateTo = LocalDate.now().minusDays(10),
      )
      assertBadRequestWithExpectedMessage(
        request = request,
        expectedMessage = "request.dateTo is before request.dateFrom",
      )
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        " | ",
        " '' | '' ",
      ],
      delimiterString = "|",
    )
    fun `should return status 400 error when sarCaseReferenceNumber is null or empty`() {
      val request = RenderRequestEntity(
        id = UUID.randomUUID(),
        serviceConfigurationId = UUID.randomUUID(),
        nomisId = "A",
        dateFrom = LocalDate.now().minusDays(10),
        dateTo = LocalDate.now(),
      )
      assertBadRequestWithExpectedMessage(
        request = request,
        expectedMessage = "request.sarCaseReferenceNumber was null or empty",
      )
    }

    @Test
    fun `should return status 400 when a service configuration is not found for the provided Id`() {
      val serviceConfiguration = serviceConfiguration(serviceName = "no-existy", serviceLabel = "not a service")
      val renderRequestEntity = newRenderRequestFor(serviceConfiguration)

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus()
        .isEqualTo(404)
        .expectBody().jsonPath("$.errorCode").isEqualTo("2002")

      sarDataSourceApi.verifyGetSubjectAccessRequestDataNeverCalled()
    }

    private fun assertBadRequestWithExpectedMessage(request: RenderRequestEntity, expectedMessage: String) {
      sendRenderTemplateRequest(renderRequestEntity = request)
        .expectStatus().isEqualTo(400)
        .expectBody()
        .jsonPath("$.developerMessage").value { value: String ->
          assertThat(value).startsWith(expectedMessage)
        }
        .jsonPath("$.errorCode").isEqualTo("1001")

      sarDataSourceApi.verifyGetSubjectAccessRequestDataNeverCalled()
    }
  }

  private fun assertStoredTemplateVersionMatchesExpected(renderRequest: RenderRequest, expected: String?) {
    assertThat(s3TestUtil.getTemplateVersion(renderRequest.documentHtmlKey())).isEqualTo(expected)
  }

  private fun assertBucketContentMatchesExpected(key: String, expectedContent: String?) {
    val s3Content = s3TestUtil.getFile(key)
    assertThat(s3Content).isNotNull().isNotEmpty().isEqualTo(expectedContent)
  }

  private fun assertUploadedHtmlMatchesExpected(renderRequest: RenderRequest, expectedHtml: String) {
    val uploadedFile = s3TestUtil.getFile(renderRequest.documentHtmlKey())
    assertThat(uploadedFile).isNotNull().isNotEmpty().isEqualTo(expectedHtml)
  }

  private fun assertUploadedAttachmentMatchesExpected(
    renderRequest: RenderRequest,
    expectedAttachmentBytes: ByteArray,
    expectedMetadata: AttachmentMetadata,
  ) {
    val uploadedFile = s3TestUtil.getFileBytes(
      renderRequest.documentAttachmentKey(
        Integer.parseInt(expectedMetadata.attachmentNumber),
        expectedMetadata.filename,
      ),
    )
    val metadata = s3TestUtil.getAttachmentMetadata(
      renderRequest.documentAttachmentKey(
        Integer.parseInt(expectedMetadata.attachmentNumber),
        expectedMetadata.filename,
      ),
    )
    assertThat(uploadedFile).isNotNull().isNotEmpty().isEqualTo(expectedAttachmentBytes)
    assertThat(metadata).isEqualTo(expectedMetadata)
  }

  private fun assertLegacyFunctionalityServiceDataJsonFileDoesNotExistsInBucket(request: RenderRequest) {
    assertDocumentDoesNotExists("${request.id}/${request.serviceConfiguration.serviceName}.json")
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

  private fun hmppsServiceReturnsAttachmentForRequest(
    filename: String,
    contentType: String,
  ) = hmppsServiceReturnsAttachmentForRequest(
    filename,
    contentType,
    getResourceAsByteArray("/attachments/$filename"),
  )

  private fun hmppsServiceReturnsAttachmentForRequest(
    filename: String,
    contentType: String,
    content: ByteArray,
  ) = sarDataSourceApi
    .stubGetAttachment(
      contentType = contentType,
      content = content,
      filename = filename,
    )

  private fun hmppsServiceReturnsErrorForAttachmentRequest(
    filename: String,
    contentType: String,
    status: HttpStatus,
  ) = sarDataSourceApi
    .stubGetAttachment(
      contentType = contentType,
      filename = filename,
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(status.value())
        .withHeader("Content-Type", "application/json")
        .withBody("""{ "error": "some error" "}"""),
    )

  private fun assertRenderTemplateSuccessResponse(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
  ) = response.expectStatus()
    .isEqualTo(HttpStatus.CREATED)
    .expectBody()
    .jsonPath("documentKey").isEqualTo("${renderRequest.id}/${renderRequest.serviceConfiguration.serviceName}.html")

  private fun sendRenderTemplateRequest(
    role: String = "ROLE_SAR_DATA_ACCESS",
    renderRequestEntity: RenderRequestEntity,
  ): WebTestClient.ResponseSpec = webTestClient
    .post()
    .uri("/subject-access-request/render")
    .header("Content-Type", "application/json")
    .headers(setAuthorisation(roles = listOf(role)))
    .bodyValue(objectMapper.writeValueAsString(renderRequestEntity))
    .exchange()

  private fun getDataRetryEventProperties(
    renderRequest: RenderRequest,
    attempt: Int,
    responseStatus: Int,
  ) = eventProperties(
    renderRequest,
    "uri" to renderRequest.serviceConfiguration.url,
    "error" to "GET ${getSarRequestUrl(renderRequest)}, status: $responseStatus",
    "retryAttempts" to attempt.toString(),
    "backOff" to webClientConfiguration.backOff,
    "maxRetries" to webClientConfiguration.maxRetries.toString(),
  )

  private fun getAttachmentRetryEventProperties(
    renderRequest: RenderRequest,
    filename: String,
    attempt: Int,
  ) = getAttachmentRetryEventProperties(
    renderRequest,
    filename,
    "500 Internal Server Error from GET http://localhost:${sarDataSourceApi.port()}/attachments/$filename",
    attempt,
  )

  private fun getAttachmentRetryEventProperties(
    renderRequest: RenderRequest,
    filename: String,
    error: String,
    attempt: Int,
  ) = eventProperties(
    renderRequest,
    "uri" to "http://localhost:${sarDataSourceApi.port()}/attachments/$filename",
    "error" to error,
    "retryAttempts" to attempt.toString(),
    "backOff" to webClientConfiguration.backOff,
    "maxRetries" to webClientConfiguration.maxRetries.toString(),
  )

  private fun dataRequestErroredEventProperties(renderRequest: RenderRequest, status: Int) = eventProperties(
    renderRequest,
    "uri" to renderRequest.serviceConfiguration.url,
    "errorMessage" to retryExhaustedErrorMessage(renderRequest, status),
  )

  private fun attachmentRequestErroredEventProperties(renderRequest: RenderRequest, filename: String) = eventProperties(
    renderRequest,
    "uri" to "http://localhost:${sarDataSourceApi.port()}/attachments/$filename",
    "errorMessage" to retryExhaustedErrorMessage(
      renderRequest,
      "http://localhost:${sarDataSourceApi.port()}/attachments/$filename",
    ),
  )

  private fun requestErroredEventProperties(
    renderRequest: RenderRequest,
    uri: String,
    causeMessage: String,
  ) = eventProperties(
    renderRequest,
    "uri" to uri,
    "errorMessage" to retryExhaustedErrorMessage(renderRequest, causeMessage, uri),
  )

  private fun retryExhaustedErrorMessage(
    renderRequest: RenderRequest,
    causeMessage: String,
    uri: String,
  ) = "request failed and max retry attempts (${webClientConfiguration.maxRetries}) exhausted, cause=$causeMessage, " +
    "errorCode=${ErrorCode.WEB_CLIENT_RETRY_EXHAUSTED.codeString()}, id=${renderRequest.id}, serviceName=${renderRequest.serviceConfiguration.serviceName}, uri=$uri"

  private fun retryExhaustedErrorMessage(
    renderRequest: RenderRequest,
    uri: String,
  ) = retryExhaustedErrorMessage(renderRequest, "500 Internal Server Error from GET $uri", uri)

  private fun retryExhaustedErrorMessage(
    renderRequest: RenderRequest,
    status: Int,
  ): String = "request failed and max retry attempts (${webClientConfiguration.maxRetries}) exhausted, " +
    "cause=GET ${getSarRequestUrl(renderRequest)}, status: $status, errorCode=" +
    "${ErrorCode.WEB_CLIENT_RETRY_EXHAUSTED.codeString()}, id=${renderRequest.id}, serviceName=" +
    "${renderRequest.serviceConfiguration.serviceName}, uri=${renderRequest.serviceConfiguration.url}"

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

  private fun assertNoTemplateVersionExistsForService(serviceConfiguration: ServiceConfiguration) {
    templateVersionService.getTemplateVersionsForServiceConfigurationId(serviceConfiguration).isEmpty()
  }
}
