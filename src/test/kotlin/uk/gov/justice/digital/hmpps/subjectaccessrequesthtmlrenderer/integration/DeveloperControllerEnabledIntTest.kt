package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@TestPropertySource(
  properties = ["developer-endpoint.enabled=true"],
)
class DeveloperControllerEnabledIntTest : IntegrationTestBase() {

  @BeforeEach
  fun setup() {
    s3TestUtil.clearBucket()
  }

  @AfterEach
  fun tearDown() {
    s3TestUtil.clearBucket()
  }

  @Nested
  inner class GetRenderedHtml {

    @Test
    fun `should return status 404 if requested document does not exist`() {
      val subjectAccessRequestId = UUID.randomUUID()

      webTestClient.get()
        .uri("/subject-access-request/$subjectAccessRequestId/service-xyz")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(HttpStatus.NOT_FOUND.value())
        .jsonPath("$.errorCode").isEmpty()
        .jsonPath("$.userMessage").isEqualTo("resource not found, id=$subjectAccessRequestId, documentKey=$subjectAccessRequestId/service-xyz.html")
    }

    @Test
    fun `should return status 200 and expected content when requested file exists in bucket`(): Unit = runBlocking {
      val subjectAccessRequestId = UUID.randomUUID()
      val serviceName = "service-xyz"
      s3TestUtil.addFilesToBucket(S3File("$subjectAccessRequestId/$serviceName.html"))

      val resp = webTestClient.get()
        .uri("/subject-access-request/$subjectAccessRequestId/$serviceName")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .returnResult()

      assertThat(resp.responseBody).isNotNull()
      assertThat(String(resp.responseBody!!)).isEqualTo(fileContent)
    }
  }

  @Nested
  inner class ListFileSummary {

    @Test
    fun `should return status not found when subject access request ID does not exist in the bucket`(): Unit = runBlocking {
      val sarId = UUID.randomUUID()

      webTestClient.get()
        .uri("/subject-access-request/$sarId")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(HttpStatus.NOT_FOUND.value())
        .jsonPath("$.errorCode").isEmpty()
        .jsonPath("$.userMessage").isEqualTo("resource not found, id=$sarId")
    }

    @Test
    fun `should return expected list of files`(): Unit = runBlocking {
      val id1 = UUID.randomUUID()
      val id2 = UUID.randomUUID()

      s3TestUtil.addFilesToBucket(
        S3File("$id1/service-A.html"),
        S3File("$id1/service-B.html"),
        S3File("$id1/service-C.html"),
        S3File("$id2/service-X.html"),
        S3File("$id2/service-Y.html"),
        S3File("$id2/service-Z.html"),
      )

      webTestClient.get()
        .uri("/subject-access-request/$id1")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("$.files").isArray
        .jsonPath("$.files.length()").isEqualTo(3)
        .jsonPath("$.files[0].key").isEqualTo("$id1/service-A.html")
        .jsonPath("$.files[0].lastModified").isNotEmpty
        .jsonPath("$.files[0].size").isEqualTo(fileContent.toByteArray().size)
        .jsonPath("$.files[1].key").isEqualTo("$id1/service-B.html")
        .jsonPath("$.files[1].lastModified").isNotEmpty
        .jsonPath("$.files[1].size").isEqualTo(fileContent.toByteArray().size)
        .jsonPath("$.files[2].key").isEqualTo("$id1/service-C.html")
        .jsonPath("$.files[2].lastModified").isNotEmpty
        .jsonPath("$.files[2].size").isEqualTo(fileContent.toByteArray().size)
    }

    @Test
    fun `should only return files with key ending with dot html `(): Unit = runBlocking {
      val id = UUID.randomUUID()
      s3TestUtil.addFilesToBucket(
        S3File("$id/service-A.html"),
        S3File("$id/service-A.txt"),
      )

      webTestClient.get()
        .uri("/subject-access-request/$id")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("$.files").isArray
        .jsonPath("$.files.length()").isEqualTo(1)
        .jsonPath("$.files[0].key").isEqualTo("$id/service-A.html")
        .jsonPath("$.files[0].lastModified").isNotEmpty
        .jsonPath("$.files[0].size").isEqualTo(fileContent.toByteArray().size)
    }
  }
}
