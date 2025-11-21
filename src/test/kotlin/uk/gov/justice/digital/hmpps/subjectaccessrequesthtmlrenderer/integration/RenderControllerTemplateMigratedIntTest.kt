package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.ServiceData
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequestEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(SarDataSourceApiExtension::class)
class RenderControllerTemplateMigratedIntTest : IntegrationTestBase() {

  private val sarDataJsonFilePath =
    "/integration-tests.service-response-stubs/hmpps-test-service-migrated-template-response.json"

  private val templateV1Path = "/templates/hmpps-test-service-migrated-template-v1.mustache"
  private val templateV1Hash = "6dce3e24130bdefd8a683c020711585dea53c99dec763cc2655023d6be55bfef"

  private val templateV2Path = "/templates/hmpps-test-service-migrated-template-v2.mustache"
  private val templateV2Hash = "b8d6be8b37d1f2c07c243b09a6863f31bfa4ce53cb5c9e5cf1ba14dec18befc5"

  private val serviceName = "hmpps-test-service-migrated-template"
  private val serviceLabel = "HMPPS Test Service Template Migrated"

  private val testServiceConfiguration = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = serviceName,
    label = serviceLabel,
    url = "http://localhost:${sarDataSourceApi.port()}",
    enabled = true,
    order = 666,
    templateMigrated = true,
  )

  private val templateVersion1Published = TemplateVersion(
    serviceConfiguration = testServiceConfiguration,
    status = TemplateVersionStatus.PUBLISHED,
    version = 1,
    createdAt = LocalDateTime.now().minusDays(2),
    publishedAt = LocalDateTime.now().minusHours(1),
    fileHash = templateV1Hash,
  )

  private val templateVersion2Published = TemplateVersion(
    serviceConfiguration = testServiceConfiguration,
    status = TemplateVersionStatus.PUBLISHED,
    version = 2,
    createdAt = LocalDateTime.now().minusDays(1),
    publishedAt = LocalDateTime.now().minusHours(12),
    fileHash = templateV2Hash,
  )

  private val templateVersion3Pending = TemplateVersion(
    serviceConfiguration = testServiceConfiguration,
    status = TemplateVersionStatus.PENDING,
    version = 3,
    createdAt = LocalDateTime.now().minusHours(12),
    publishedAt = null,
    fileHash = templateV2Hash,
  )

  @Autowired
  private lateinit var templateVersionRepository: TemplateVersionRepository

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling out to external APIs
    clearAuthorizedClientsCache("sar-html-renderer-client", "anonymousUser")
    s3TestUtil.clearBucket()

    serviceConfigurationRepository.save(testServiceConfiguration)
  }

  @AfterEach
  fun teardown() {
    templateVersionRepository.deleteAll()
    serviceConfigurationRepository.deleteById(testServiceConfiguration.id)
  }

  @Nested
  inner class Success {

    @Test
    fun `should generate expected HTML for service with template migrated true`() {
      templateVersionRepository.save(templateVersion1Published)

      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)
      hmppsServiceReturnServiceTemplate(getResourceAsString(templateV1Path))

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled()

      assertUploadedJsonMatchesExpected(renderRequest, getServiceResponseBody(serviceName))
      assertUploadedHtmlMatchesExpected(
        renderRequest,
        getExpectedHtmlString("hmpps-test-service-migrated-template-v1"),
      )
    }

    @Test
    fun `should return success get service template hash matches the PENDING template version`() {
      templateVersionRepository.saveAll(
        listOf(
          templateVersion1Published,
          templateVersion2Published,
          templateVersion3Pending,
        ),
      )

      assertThatTemplateVersionIsPending(templateVersion3Pending.id)

      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)
      hmppsServiceReturnServiceTemplate(getResourceAsString(templateV2Path))

      val response = sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)

      assertRenderTemplateSuccessResponse(response, renderRequest)
      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled()

      assertUploadedJsonMatchesExpected(renderRequest, getServiceResponseBody(serviceName))
      assertUploadedHtmlMatchesExpected(renderRequest, getExpectedHtmlString("hmpps-test-service-migrated-template-v2"))
      assertThatTemplateVersionIsPublished(templateVersion3Pending.id)
    }
  }

  @Nested
  inner class Failure {

    @Test
    fun `should return error when no template version exists for service with migrated template`() {
      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)
      hmppsServiceReturnServiceTemplateNotFoundError()

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo("3003")
        .jsonPath("$.developerMessage").value { value: String ->
          assertThat(value).startsWith("Get Service template request returned status not found")
        }

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled(1)
    }

    @Test
    fun `should return error when get service template request is unsuccessful`() {
      templateVersionRepository.save(templateVersion1Published)

      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)
      hmppsServiceReturnServiceTemplateError()

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo("2000")
        .jsonPath("$.developerMessage").value { value: String ->
          assertThat(value).startsWith("request failed and max retry attempts (2) exhausted")
        }

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled(times = 3)
    }

    @Test
    fun `should return error when get service template hash does not match registered template versions`() {
      templateVersion1Published.fileHash = "some random hash value"
      templateVersionRepository.save(templateVersion1Published)

      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)
      hmppsServiceReturnServiceTemplate(templateV1Path)

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo("3001")
        .jsonPath("$.developerMessage").value { value: String ->
          assertThat(value).startsWith("service template file hash does not match registered template versions")
        }

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled()
    }

    @Test
    fun `should not be successful when a superseded PUBLISHED template is returned`() {
      templateVersionRepository.saveAll(
        listOf(
          templateVersion1Published,
          templateVersion2Published,
        ),
      )

      val renderRequestEntity = newRenderRequestFor(testServiceConfiguration)
      val renderRequest = RenderRequest(renderRequestEntity, testServiceConfiguration)

      assertServiceJsonDocumentDoesNotAlreadyExist(renderRequest)
      assertServiceHtmlDocumentDoesNotAlreadyExist(renderRequest)
      hmppsAuthReturnsValidAuthToken()
      hmppsServiceReturnsDataForRequest(renderRequest)

      // Latest published version is V2, Service returns Template V1
      hmppsServiceReturnServiceTemplate(getResourceAsString(templateV1Path))

      sendRenderTemplateRequest(renderRequestEntity = renderRequestEntity)
        .expectStatus().isEqualTo(500)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo("3001")
        .jsonPath("$.developerMessage").value { value: String ->
          assertThat(value).startsWith("service template file hash does not match registered template versions")
        }

      hmppsAuth.verifyGrantTokenIsCalled(1)
      sarDataSourceApi.verifyGetSubjectAccessRequestDataCalled()
      sarDataSourceApi.verifyGetTemplateCalled()

      assertUploadedJsonMatchesExpected(renderRequest, getServiceResponseBody(serviceName))
      assertUploadedHtmlDoesNotExist(renderRequest)
    }
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

  private fun assertUploadedJsonMatchesExpected(renderRequest: RenderRequest, expectedJson: String) {
    val uploadedFile = s3TestUtil.getFile(renderRequest.documentJsonKey())
    assertThat(uploadedFile).isNotNull().isNotEmpty()
    assertThat(objectMapper.readValue(uploadedFile, ServiceData::class.java)).isEqualTo(
      objectMapper.readValue(
        expectedJson,
        ServiceData::class.java,
      ),
    )
  }

  private fun assertUploadedHtmlMatchesExpected(renderRequest: RenderRequest, expectedHtml: String) {
    val uploadedFile = s3TestUtil.getFile(renderRequest.documentHtmlKey())
    assertThat(uploadedFile).isNotNull().isNotEmpty().isEqualTo(expectedHtml)
  }

  private fun assertUploadedHtmlDoesNotExist(renderRequest: RenderRequest) {
    assertThat(s3TestUtil.documentExists(renderRequest.documentHtmlKey())).isFalse()
  }

  private fun assertRenderTemplateSuccessResponse(
    response: WebTestClient.ResponseSpec,
    renderRequest: RenderRequest,
  ) = response.expectStatus()
    .isEqualTo(HttpStatus.CREATED)
    .expectBody()
    .jsonPath("documentKey").isEqualTo("${renderRequest.id}/${renderRequest.serviceConfiguration.serviceName}.html")

  private fun hmppsServiceReturnsDataForRequest(request: RenderRequest) = sarDataSourceApi
    .stubGetSubjectAccessRequestData(
      params = request.toGetSubjectAccessRequestDataParams(),
      responseDefinition = ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(getResourceAsString(sarDataJsonFilePath)),
    )

  private fun hmppsServiceReturnServiceTemplate(
    templateBody: String,
  ) = sarDataSourceApi.stubGetTemplate(
    ResponseDefinitionBuilder
      .responseDefinition()
      .withStatus(200)
      .withBody(templateBody),
  )

  private fun hmppsServiceReturnServiceTemplateError() = sarDataSourceApi.stubGetTemplate(
    ResponseDefinitionBuilder
      .responseDefinition()
      .withStatus(500)
      .withBody("failed to get service template"),
  )

  private fun hmppsServiceReturnServiceTemplateNotFoundError() = sarDataSourceApi.stubGetTemplate(
    ResponseDefinitionBuilder
      .responseDefinition()
      .withStatus(404)
      .withBody("resource not found"),
  )

  private fun assertThatTemplateVersionIsPending(id: UUID) {
    val actual = templateVersionRepository.findByIdOrNull(id)
    assertThat(actual?.status).isEqualTo(TemplateVersionStatus.PENDING)
    assertThat(actual?.publishedAt).isNull()
  }

  private fun assertThatTemplateVersionIsPublished(id: UUID) {
    val actual = templateVersionRepository.findByIdOrNull(id)
    assertThat(actual?.status).isEqualTo(TemplateVersionStatus.PUBLISHED)
    assertThat(actual?.publishedAt).isNotNull()
    assertThat(actual!!.publishedAt).isBefore(LocalDateTime.now())
    assertThat(actual.publishedAt).isAfter(actual.createdAt)
  }
}
