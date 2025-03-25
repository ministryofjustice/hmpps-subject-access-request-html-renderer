package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@TestPropertySource(
  properties = ["developer-endpoint.enabled=true"],
)
class DeveloperControllerEnabledIntTest : IntegrationTestBase() {

  private companion object {
    const val FILE_CONTENT = "Woe to you, Oh earth and sea, For the Devil sends the beast with wrath..."
  }

  @BeforeEach
  fun setup() {
    clearS3Bucket()
  }

  @AfterEach
  fun tearDown() {
    clearS3Bucket()
  }

  @Nested
  inner class GetRenderedHtml {

    @Test
    fun `should return status 404 if requested document does not exist`() {
      webTestClient.get()
        .uri("/subject-access-request/${UUID.randomUUID()}/service-xyz")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should return status 200 and expected content when requested file exists in bucket`(): Unit = runBlocking {
      val subjectAccessRequestId = UUID.randomUUID()
      val serviceName = "service-xyz"
      populateBucket(BucketFile(id = subjectAccessRequestId, serviceName = serviceName))

      val resp = webTestClient.get()
        .uri("/subject-access-request/$subjectAccessRequestId/$serviceName")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .returnResult()

      assertThat(resp.responseBody).isNotNull()
      assertThat(String(resp.responseBody!!)).isEqualTo(FILE_CONTENT)
    }
  }

  @Nested
  inner class ListReportFiles {

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
    fun `should return expected list of files`(): Unit = runBlocking {
      val sarId1 = UUID.randomUUID()
      val sarId2 = UUID.randomUUID()

      populateBucket(
        BucketFile(id = sarId1, serviceName = "service-A"),
        BucketFile(id = sarId1, serviceName = "service-B"),
        BucketFile(id = sarId1, serviceName = "service-C"),
        BucketFile(id = sarId2, serviceName = "service-X"),
        BucketFile(id = sarId2, serviceName = "service-Y"),
        BucketFile(id = sarId2, serviceName = "service-Z"),
      )

      webTestClient.get()
        .uri("/subject-access-request/$sarId1")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("$.files").isArray
        .jsonPath("$.files.length()").isEqualTo(3)
        .jsonPath("$.files[0]").isEqualTo("$sarId1/service-A.html")
        .jsonPath("$.files[1]").isEqualTo("$sarId1/service-B.html")
        .jsonPath("$.files[2]").isEqualTo("$sarId1/service-C.html")
    }

    @Test
    fun `should only return files with key ending with dot html `(): Unit = runBlocking {
      val sarId1 = UUID.randomUUID()
      populateBucket(
        BucketFile(id = sarId1, serviceName = "service-A", extension = "html"),
        BucketFile(id = sarId1, serviceName = "service-A", extension = "txt"),
      )

      webTestClient.get()
        .uri("/subject-access-request/$sarId1")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("$.files").isArray
        .jsonPath("$.files.length()").isEqualTo(1)
        .jsonPath("$.files[0]").isEqualTo("$sarId1/service-A.html")
    }
  }

  suspend fun populateBucket(vararg bucketFiles: BucketFile) {
    bucketFiles.forEach { file ->
      s3.putObject {
        bucket = s3Properties.bucketName
        key = "${file.id}/${file.serviceName}.${file.extension}"
        body = ByteStream.fromString(FILE_CONTENT)
      }
    }
  }

  data class BucketFile(val id: UUID, val serviceName: String, val extension: String = "html")
}
