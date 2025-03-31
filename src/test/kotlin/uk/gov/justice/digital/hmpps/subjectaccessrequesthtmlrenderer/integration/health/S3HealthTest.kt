package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.wiremock.NomisMappingsApiExtension.Companion.nomisMappingsApi

class S3HealthTest : IntegrationTestBase() {

  @Test
  fun `S3 Health check returns OK`() {
    hmppsAuth.stubHealthPing(200)
    locationsApi.stubHealthPing(200)
    nomisMappingsApi.stubHealthPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.s3Health.status").isEqualTo("UP")
      .jsonPath("components.s3Health.details.bucket").isEqualTo(s3Properties.bucketName)
      .jsonPath("components.s3Health.details.region").isEqualTo(s3Properties.region)
      .returnResult()
  }
}
