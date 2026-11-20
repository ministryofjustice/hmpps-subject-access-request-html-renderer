package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_TEMPLATE_SUCCESS
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.REQUEST_RECEIVED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_RENDERED_HTML_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequestEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionHealthStatusRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.TemplateVersionService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments.InlineAttachmentFile
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments.InlineAttachmentFileService
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

@DirtiesContext()
@TestPropertySource(
  properties = [
    "inline-attachment-renderer.use=s3-renderer",
  ],
)
class RenderControllerInlineAttachmentsIntTest : IntegrationTestBase() {

  @MockitoBean
  private lateinit var inlineAttachmentFileService: InlineAttachmentFileService

  @Autowired
  private lateinit var templateVersionRepository: TemplateVersionRepository

  @Autowired
  private lateinit var templateVersionHealthStatusRepository: TemplateVersionHealthStatusRepository

  @Autowired
  private lateinit var templateVersionService: TemplateVersionService

  // Use fixed IDs to match the values in the reference HTML file.
  private val requestId = UUID.fromString("b10a2cbc-998d-4353-a0b4-c993d964517b")
  private val mapJpgAttachmentId = UUID.fromString("35eab234-d32f-4008-9ad7-9af5aef7e985")
  private val smallJpgAttachmentId = UUID.fromString("d3e0ace5-3bd5-4f88-bf33-4343c15fd89e")

  private val testServiceConfiguration = ServiceConfiguration(
    serviceName = "test-service-inline-attachments",
    label = "Test Service Inline Attachments",
    url = "http://localhost:8092",
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
    suspended = false,
  )

  private val templateVersion = TemplateVersion(
    id = UUID.randomUUID(),
    serviceConfiguration = testServiceConfiguration,
    version = 1,
    createdAt = LocalDateTime.now().minusHours(2),
    publishedAt = LocalDateTime.now().minusHours(1),
    fileHash = generateFileHash(
      getResourceAsByteArray(
        filepath = "/templates/${testServiceConfiguration.serviceName}.mustache",
      ),
    ),
    status = TemplateVersionStatus.PUBLISHED,
  )

  private val templateVersionHealthStatus = TemplateVersionHealthStatus(
    id = UUID.randomUUID(),
    serviceConfiguration = testServiceConfiguration,
    status = HealthStatusType.HEALTHY,
    lastModified = Instant.now(),
  )

  private val renderRequestEntity = newRenderRequestFor(
    serviceConfiguration = testServiceConfiguration,
    id = requestId,
  )

  private val renderRequest = RenderRequest(
    request = renderRequestEntity,
    serviceConfiguration = testServiceConfiguration,
  )

  private val renderRequestInfo = RenderRequestInfo(
    id = renderRequest.id,
    serviceName = testServiceConfiguration.serviceName,
  )

  private val mapJpgAttachmentData = getResourceAsByteArray("/attachments/map.jpg")
  private val mapJpgAttachmentFile = InlineAttachmentFile(
    requestId = renderRequest.id,
    serviceName = testServiceConfiguration.serviceName,
    contentType = "image/jpeg",
    filename = "$mapJpgAttachmentId.jpg",
    data = mapJpgAttachmentData,
    filesize = mapJpgAttachmentData.size.toLong(),
  )

  private val smallJpgAttachmentData = getResourceAsByteArray("/attachments/small.jpg")
  private val smallJpgAttachmentFile = InlineAttachmentFile(
    requestId = renderRequest.id,
    serviceName = testServiceConfiguration.serviceName,
    contentType = "image/jpeg",
    filename = "$smallJpgAttachmentId.jpg",
    data = smallJpgAttachmentData,
    filesize = smallJpgAttachmentData.size.toLong(),
  )

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
    clearAuthorizedClientsCache("sar-html-renderer-client", "anonymousUser")
    s3TestUtil.clearBucket()

    serviceConfigurationRepository.saveAndFlush(testServiceConfiguration)
    templateVersionRepository.saveAndFlush(templateVersion)
    templateVersionHealthStatusRepository.saveAndFlush(templateVersionHealthStatus)
  }

  @AfterEach
  fun cleanup() {
    templateVersionService.deleteTemplateVersionByServiceConfiguration(testServiceConfiguration)
    serviceConfigurationRepository.delete(testServiceConfiguration)
    s3TestUtil.clearBucket()
  }

  @Test
  fun `should generate expected html with inline attachments and return status 201`() {
    assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
    hmppsAuthReturnsValidAuthToken()
    hmppsServiceReturnsDataForRequest(renderRequest, testServiceConfiguration.serviceName)
    hmppsServiceReturnsServiceTemplate(testServiceConfiguration.serviceName)

    inlineAttachmentFileServiceReturns(mapJpgAttachmentFile, renderRequestInfo)
    inlineAttachmentFileServiceReturns(smallJpgAttachmentFile, renderRequestInfo)

    hmppsServiceReturnsAttachmentForRequest("map.jpg", "image/jpeg")
    hmppsServiceReturnsAttachmentForRequest("small.jpg", "image/jpeg")

    val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

    assertRenderTemplateSuccessResponse(response, renderRequest, "1")
    hmppsAuth.verifyGrantTokenIsCalled(1)
    sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()

    assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString(testServiceConfiguration.serviceName))

    assertExpectedInlineAttachmentFileUploadedToBucket(mapJpgAttachmentFile.documentKey, mapJpgAttachmentData)
    assertExpectedInlineAttachmentFileUploadedToBucket(smallJpgAttachmentFile.documentKey, smallJpgAttachmentData)
    assertStoredTemplateVersionMatchesExpected(renderRequest, "v1")

    assertTelemetryEvents(
      ExpectedTelemetryEvent(REQUEST_RECEIVED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(GET_SERVICE_DATA, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(SERVICE_DATA_RETURNED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(RENDER_TEMPLATE_STARTED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(GET_SERVICE_TEMPLATE_SUCCESS, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(RENDER_TEMPLATE_COMPLETED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(STORE_RENDERED_HTML_STARTED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(STORE_RENDERED_HTML_COMPLETED, eventProperties(renderRequest)),
      ExpectedTelemetryEvent(REQUEST_COMPLETE, eventProperties(renderRequest)),
    )

    sarDataSourceApi.verifyGetTemplateCalled(times = 1)
  }

  private fun inlineAttachmentFileServiceReturns(
    inlineAttachmentFile: InlineAttachmentFile,
    renderRequest: RenderRequestInfo,
  ) {
    whenever(
      inlineAttachmentFileService.createInlineAttachmentFile(
        renderRequestInfo = eq(
          RenderRequestInfo(
            id = renderRequest.id,
            serviceName = testServiceConfiguration.serviceName,
          ),
        ),
        attachmentInfo = any(),
        attachmentData = eq(inlineAttachmentFile.data),
      ),
    )
      .thenReturn(inlineAttachmentFile)
  }

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

  private fun assertRenderTemplateSuccessResponse(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
    templateVersion: String,
  ) = response.expectStatus()
    .isEqualTo(HttpStatus.CREATED)
    .expectBody()
    .jsonPath("documentKey").isEqualTo("${renderRequest.id}/${renderRequest.serviceConfiguration.serviceName}.html")
    .jsonPath("templateVersion").isEqualTo(templateVersion)

  private fun assertExpectedInlineAttachmentFileUploadedToBucket(documentKey: String, expectedData: ByteArray) {
    val uploadedFile = s3TestUtil.getFileBytes(documentKey)
    assertThat(uploadedFile).isNotNull().isNotEmpty().isEqualTo(expectedData)
  }

  override fun assertUploadedHtmlMatchesExpected(renderRequest: RenderRequest, expectedHtml: String) {
    // Format the HTML consistently to remove any whitespace/formatting differences that don't really matter.
    val uploadedFormatted = Jsoup.parse(s3TestUtil.getFile(renderRequest.documentHtmlKey())).html()
    val actualFormatted = Jsoup.parse(expectedHtml).html()
    assertThat(uploadedFormatted).isEqualTo(actualFormatted)
  }
}
