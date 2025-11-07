package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.ServiceConfigurationRepository
import java.util.Optional
import java.util.UUID

class ServiceConfigurationServiceTest {

  private val serviceConfigurationRepository: ServiceConfigurationRepository = mock()

  private val serviceConfigurationService: ServiceConfigurationService = ServiceConfigurationService(
    serviceConfigurationRepository,
    G1_API_URL,
    G2_API_URL,
    G3_API_URL,
  )

  companion object {
    private const val G1_API_URL = "https://www.g1-resolved.com"
    private const val G2_API_URL = "https://www.g2-resolved.com"
    private const val G3_API_URL = "https://www.g3-resolved.com"
  }

  @Nested
  inner class ResolveUrlPlaceHolder {

    @ParameterizedTest
    @CsvSource(
      value = [
        "G1                         | $G1_API_URL",
        "G2                         | $G2_API_URL",
        "G3                         | $G3_API_URL",
        "hmpps-book-secure-move-api | hmpps-book-secure-move-api",
        "hmpps-uof-data-api         | hmpps-uof-data-api",
        "make-recall-decision-api   | make-recall-decision-api",
      ],
      delimiter = '|',
    )
    fun `should resolve URL when service name is G1, G2, G3`(serviceName: String, expectedUrl: String) {
      val actual = serviceConfigurationService.resolveUrlPlaceHolder(
        ServiceConfiguration(
          serviceName = serviceName,
          url = serviceName,
          enabled = true,
          label = serviceName,
          templateMigrated = false,
          order = 1,
        ),
      )

      assertThat(actual).isEqualTo(expectedUrl)
    }
  }

  @Nested
  inner class FindByIdOrNull {

    @Test
    fun `should return null when repository returns null`() {
      whenever(serviceConfigurationRepository.findById(any()))
        .thenReturn(Optional.empty())

      assertThat(serviceConfigurationService.findByIdOrNull(UUID.randomUUID()))
        .isNull()
    }

    @Test
    fun `should return service configuration when repository returns object`() {
      val expected: ServiceConfiguration = mock()
      whenever(serviceConfigurationRepository.findById(any()))
        .thenReturn(Optional.of(expected))

      assertThat(serviceConfigurationService.findByIdOrNull(UUID.randomUUID()))
        .isEqualTo(expected)
    }
  }

  @Nested
  inner class FindByServiceNameOrNull {

    @Test
    fun `should return null when repository returns null`() {
      whenever(serviceConfigurationRepository.findByServiceName(any()))
        .thenReturn(null)

      assertThat(serviceConfigurationService.findByIdOrNull(UUID.randomUUID()))
        .isNull()
    }

    @Test
    fun `should return service configuration when repository returns object`() {
      val expected: ServiceConfiguration = mock()
      whenever(serviceConfigurationRepository.findByServiceName(any()))
        .thenReturn(expected)

      assertThat(serviceConfigurationService.findByServiceNameOrNull("some service name"))
        .isEqualTo(expected)
    }
  }
}
