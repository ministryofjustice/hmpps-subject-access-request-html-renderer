package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import aws.sdk.kotlin.services.s3.S3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.S3TestUtil
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.NomisMappingsApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.NomisMappingsApiExtension.Companion.nomisMappingsApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.SarDataSourceApiExtension.Companion.sarDataSourceApi
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.LocalDate
import java.util.UUID

@ExtendWith(
  HmppsAuthApiExtension::class,
  SarDataSourceApiExtension::class,
  LocationsApiExtension::class,
  NomisMappingsApiExtension::class,
)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Import(S3TestUtil::class)
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  protected lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var s3: S3Client

  @Autowired
  protected lateinit var s3Properties: S3Properties

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtil

  @Autowired
  protected lateinit var webClientConfiguration: WebClientConfiguration

  @MockitoBean
  protected lateinit var telemetryClient: TelemetryClient

  @Captor
  protected lateinit var eventNameCaptor: ArgumentCaptor<String>

  @Captor
  protected lateinit var eventPropertiesCaptor: ArgumentCaptor<Map<String, String>>

  companion object {
    @JvmStatic
    protected val fileContent =
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
    locationsApi.stubHealthPing(status)
    nomisMappingsApi.stubHealthPing(status)
    sarDataSourceApi.stubHealthPing(status)
  }

  protected fun clearAuthorizedClientsCache(clientId: String, principalName: String) = oAuth2AuthorizedClientService
    .removeAuthorizedClient(clientId, principalName)

  protected fun hmppsAuthReturnsValidAuthToken() {
    hmppsAuth.stubGrantToken()
  }

  protected fun RenderRequest.toGetSubjectAccessRequestDataParams() = GetSubjectAccessRequestDataParams(
    prn = this.nomisId,
    crn = this.ndeliusId,
    dateFrom = this.dateFrom,
    dateTo = this.dateTo,
  )

  protected fun newRenderRequestFor(
    serviceName: String,
    serviceLabel: String,
    id: UUID? = UUID.randomUUID(),
  ): RenderRequest = RenderRequest(
    id = id,
    serviceName = serviceName,
    serviceLabel = serviceLabel,
    serviceUrl = "http://localhost:${sarDataSourceApi.port()}",
    nomisId = "nomis1234",
    ndeliusId = null,
    dateFrom = LocalDate.of(2020, 1, 1),
    dateTo = LocalDate.of(2021, 1, 1),
  )

  protected fun assertServiceJsonDocumentDoesNotAlreadyExist(request: RenderRequest): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentJsonKey()))
      .withFailMessage { "expected file ${request.documentJsonKey()} to not exist" }
      .isFalse()
  }

  protected fun assertServiceHtmlDocumentDoesNotAlreadyExist(request: RenderRequest): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentHtmlKey()))
      .withFailMessage { "expected file ${request.documentHtmlKey()} to not exist" }
      .isFalse()
  }

  protected fun assertServiceAttachmentDoesNotAlreadyExist(request: RenderRequest, attachmentNumber: Int, filename: String): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentAttachmentKey(attachmentNumber, filename)))
      .withFailMessage { "expected file ${request.documentAttachmentKey(attachmentNumber, filename)} to not exist" }
      .isFalse()
  }

  protected fun assertServiceJsonDocumentExists(request: RenderRequest): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentJsonKey()))
      .withFailMessage { "expected file ${request.documentJsonKey()} to exist" }
      .isTrue()
  }

  protected fun assertServiceHtmlDocumentExists(request: RenderRequest): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentHtmlKey()))
      .withFailMessage { "expected file ${request.documentHtmlKey()} to exist" }
      .isTrue()
  }

  protected fun assertServiceAttachmentExists(request: RenderRequest, attachmentNumber: Int, filename: String): Unit = runBlocking {
    assertThat(s3TestUtil.documentExists(request.documentAttachmentKey(attachmentNumber, filename)))
      .withFailMessage { "expected file ${request.documentAttachmentKey(attachmentNumber, filename)} to exist" }
      .isTrue()
  }

  protected fun assertTelemetryEvents(vararg expectedEvents: ExpectedTelemetryEvent) {
    verify(telemetryClient, times(expectedEvents.size))
      .trackEvent(capture(eventNameCaptor), capture(eventPropertiesCaptor), eq(null))

    assertThat(eventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(expectedEvents.size)
    assertThat(eventPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(expectedEvents.size)

    expectedEvents.forEachIndexed { i, expected ->
      assertThat(eventNameCaptor.allValues[i]).isEqualTo(expected.event.name)
      assertThat(eventPropertiesCaptor.allValues[i]).containsAllEntriesOf(expected.properties)
    }
  }

  protected fun eventProperties(request: RenderRequest, vararg kvPairs: Pair<String, String>) = mapOf(
    "id" to request.id.toString(),
    "serviceName" to (request.serviceName ?: ""),
    *kvPairs,
  )

  protected fun getServiceResponseBody(serviceName: String): String = getResourceAsString(
    filepath = "$SERVICE_RESPONSE_STUBS_DIR/$serviceName-response.json",
  )

  protected fun getExpectedHtmlString(filePrefix: String): String = getResourceAsString(
    filepath = "$REFERENCE_HTML_DIR/$filePrefix-expected.html",
  )

  protected fun getResourceAsString(filepath: String): String = String(getResourceAsByteArray(filepath))

  protected fun getResourceAsByteArray(filepath: String): ByteArray {
    val jsonBytes = this::class.java.getResourceAsStream(filepath).use { inputStream -> inputStream?.readAllBytes() }
    assertThat(jsonBytes).isNotNull()
    return jsonBytes!!
  }

  protected fun addServiceHtmlDocumentToBucket(renderRequest: RenderRequest): S3TestUtil.FileMetadata {
    s3TestUtil.addFilesToBucket(
      S3File(
        key = renderRequest.documentHtmlKey(),
        content = getExpectedHtmlString(renderRequest.serviceName ?: ""),
      ),
    )

    assertServiceHtmlDocumentExists(renderRequest)
    return s3TestUtil.getFileMetadata(renderRequest.documentHtmlKey())
  }

  protected fun addServiceJsonDocumentToBucket(renderRequest: RenderRequest, content: String): S3TestUtil.FileMetadata {
    s3TestUtil.addFilesToBucket(
      S3File(
        key = renderRequest.documentJsonKey(),
        content = content,
      ),
    )

    assertServiceJsonDocumentExists(renderRequest)
    return s3TestUtil.getFileMetadata(renderRequest.documentJsonKey())
  }

  protected fun addServiceJsonDocumentToBucket(renderRequest: RenderRequest): S3TestUtil.FileMetadata = addServiceJsonDocumentToBucket(renderRequest, getServiceResponseBody(renderRequest.serviceName ?: ""))

  protected fun addServiceAttachmentToBucket(renderRequest: RenderRequest, attachmentNumber: Int, filename: String) {
    s3TestUtil.addFilesToBucket(
      S3File(
        key = renderRequest.documentAttachmentKey(attachmentNumber, filename),
        content = getResourceAsString("/attachments/$filename"),
      ),
    )

    assertServiceAttachmentExists(renderRequest, attachmentNumber, filename)
  }

  data class S3File(val key: String, val content: String = fileContent)

  data class ExpectedTelemetryEvent(val event: RenderEvent, val properties: Map<String, String> = emptyMap())
}
