package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.smithy.kotlin.runtime.content.toByteArray
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.S3Properties
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

  companion object {
    private val log = LoggerFactory.getLogger(IntegrationTestBase::class.java)
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

  protected fun newRenderRequestFor(serviceName: String, id: UUID? = UUID.randomUUID()): RenderRequest = RenderRequest(
    id = id,
    serviceName = serviceName,
    serviceUrl = "http://localhost:${sarDataSourceApi.port()}",
    nomisId = "nomis1234",
    ndeliusId = "ndelius1234",
    dateFrom = LocalDate.of(2020, 1, 1),
    dateTo = LocalDate.of(2021, 1, 1),
  )

  protected fun assertServiceDocumentDoesNotAlreadyExist(request: RenderRequest): Unit = runBlocking {
    val documentExists = try {
      s3.headObject {
        bucket = s3Properties.bucketName
        key = request.documentKey()
      }
      true
    } catch (e: NotFound) {
      false
    }

    assertThat(documentExists)
      .withFailMessage { "expected file ${request.documentKey()} to not exist" }
      .isFalse()
  }

  protected fun getHtmlFileFromS3(documentKey: String): String? = runBlocking {
    s3.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = documentKey
      },
      { it.body?.toByteArray() },
    )?.let { String(it) } ?: ""
  }

  protected fun clearS3Bucket() = runBlocking {
    s3.listObjectsV2 { bucket = s3Properties.bucketName }
      .contents
      ?.map { ObjectIdentifier { key = it.key } }
      ?.takeIf { it.isNotEmpty() }
      ?.let { identifiers ->
        log.info(
          "deleting objects {} from bucket {}",
          identifiers.joinToString(",") { it.key },
          s3Properties.bucketName,
        )

        s3.deleteObjects {
          bucket = s3Properties.bucketName
          delete = Delete {
            objects = identifiers
          }
        }
      } ?: log.info("clearS3Bucket: no action required")
  }
}
