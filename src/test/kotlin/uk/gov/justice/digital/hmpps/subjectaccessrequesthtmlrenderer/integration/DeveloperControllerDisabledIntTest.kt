package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
  properties = ["developer-endpoint.enabled=false"],
)
class DeveloperControllerDisabledIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() {
    clearS3Bucket()
  }

  @Test
  fun `should return status 404`() {
    webTestClient.get()
      .uri("/subject-access-request/partials/123456789")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
      .exchange()
      .expectStatus()
      .isNotFound
  }

  @Test
  fun `should return status 404 even if requested document exists`(): Unit = runBlocking {
    val expectedContent = "Woe to you, Oh earth and sea, For the Devil sends the beast with wrath..."
    s3.putObject {
      bucket = s3Properties.bucketName
      key = "666"
      body = ByteStream.fromString(expectedContent)
    }

    webTestClient.get()
      .uri("/subject-access-request/partials/666")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
