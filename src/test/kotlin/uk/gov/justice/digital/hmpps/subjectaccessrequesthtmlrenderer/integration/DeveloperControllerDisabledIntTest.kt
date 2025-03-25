package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@TestPropertySource(
  properties = ["developer-endpoint.enabled=false"],
)
class DeveloperControllerDisabledIntTest : IntegrationTestBase() {

  private companion object {
    const val FILE_CONTENT = "Woe to you, Oh earth and sea, For the Devil sends the beast with wrath..."
  }

  @BeforeEach
  fun setup() {
    clearS3Bucket()
  }

  @Nested
  inner class GetRenderedHtml {
    @Test
    fun `should return status 404`() {
      webTestClient.get()
        .uri("/subject-access-request/${UUID.randomUUID()}/some-service")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should return status 404 even if requested document exists`(): Unit = runBlocking {
      val sarId = UUID.randomUUID()
      val serviceName = "barney-gumble"
      s3.putObject {
        bucket = s3Properties.bucketName
        key = "$sarId/$serviceName.html"
        body = ByteStream.fromString(FILE_CONTENT)
      }

      webTestClient.get()
        .uri("/subject-access-request/$sarId/$serviceName")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }

  @Nested
  inner class ListFileSummary {

    @Test
    fun `should return status not found when subject access request ID does not exist in the bucket`(): Unit = runBlocking {
      webTestClient.get()
        .uri("/subject-access-request/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should return status not found when bucket contains files for requested ID`(): Unit = runBlocking {
      val sarId = UUID.randomUUID()
      addFilesToBucket(
        S3File("$sarId/service-A"),
        S3File("$sarId/service-B"),
        S3File("$sarId/service-C"),
      )

      webTestClient.get()
        .uri("/subject-access-request/$sarId")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }
}
