package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
class TemplateVersionHealthStatusRepositoryTest {

  @Autowired
  private lateinit var templateVersionHealthStatusRepository: TemplateVersionHealthStatusRepository

  @Autowired
  private lateinit var serviceConfigurationRepository: ServiceConfigurationRepository

  private val testServiceConfig = ServiceConfiguration(
    serviceName = "service-666",
    label = "Service Six Six Six",
    url = "http://localhost:8080",
    order = 666,
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
  )

  private val initialLastModified = LocalDateTime.now().minusHours(1)

  private val healthyTemplateStatus = newTemplateVersionHealthStatus(
    serviceConfig = testServiceConfig,
    status = HealthStatusType.HEALTHY,
    lastModified = initialLastModified,
  )

  @BeforeEach
  fun setUp() {
    templateVersionHealthStatusRepository.deleteAll()
    assertThat(templateVersionHealthStatusRepository.count()).isEqualTo(0)

    serviceConfigurationRepository.deleteAll()
    assertThat(serviceConfigurationRepository.count()).isEqualTo(0)

    serviceConfigurationRepository.saveAndFlush(testServiceConfig)
  }

  @Nested
  inner class CreateTemplateVersionHealthStatus {

    @Test
    fun `should create new template version health status when no entry exists for service configuration ID`() {
      assertThat(templateVersionHealthStatusRepository.saveAndFlush(healthyTemplateStatus))
        .isEqualTo(healthyTemplateStatus)
    }

    @Test
    fun `should not create new template version health status when entry exists for service configuration ID`() {
      templateVersionHealthStatusRepository.saveAndFlush(healthyTemplateStatus)
      assertThat(templateVersionHealthStatusRepository.count()).isEqualTo(1)

      assertThrows<DataIntegrityViolationException> {
        templateVersionHealthStatusRepository.saveAndFlush(newTemplateVersionHealthStatus())
      }
    }
  }

  @Nested
  inner class FindByServiceConfigurationId {

    @Test
    fun `should find service by serviceC configuration ID`() {
      templateVersionHealthStatusRepository.saveAndFlush(healthyTemplateStatus)

      assertTemplateVersionHealthStatusEqualsExpected(
        serviceConfigurationId = testServiceConfig.id,
        expected = healthyTemplateStatus,
      )
    }
  }

  @Nested
  inner class UpdateStatusWhenChanged {

    @Test
    fun `should not update when status field has not changed`() {
      templateVersionHealthStatusRepository.saveAndFlush(healthyTemplateStatus)

      assertTemplateVersionHealthStatusEqualsExpected(
        serviceConfigurationId = testServiceConfig.id,
        expected = healthyTemplateStatus,
      )

      templateVersionHealthStatusRepository.updateStatusWhenChanged(
        serviceConfigurationId = testServiceConfig.id,
        newStatus = HealthStatusType.HEALTHY,
        lastModified = initialLastModified.plusMinutes(30),
      )

      val actual = templateVersionHealthStatusRepository.findByServiceConfigurationId(testServiceConfig.id)
      assertThat(actual).isNotNull
      assertThat(actual!!.status).isEqualTo(HealthStatusType.HEALTHY)
      assertThat(actual.lastModified).isEqualTo(initialLastModified)
    }

    @Test
    fun `should update when status field has changed`() {
      templateVersionHealthStatusRepository.saveAndFlush(healthyTemplateStatus)

      assertTemplateVersionHealthStatusEqualsExpected(
        serviceConfigurationId = testServiceConfig.id,
        expected = healthyTemplateStatus,
      )

      val updatedLastModified = initialLastModified.plusMinutes(30)

      templateVersionHealthStatusRepository.updateStatusWhenChanged(
        serviceConfigurationId = testServiceConfig.id,
        newStatus = HealthStatusType.UNHEALTHY,
        lastModified = updatedLastModified,
      )

      val actual = templateVersionHealthStatusRepository.findByServiceConfigurationId(testServiceConfig.id)
      assertThat(actual).isNotNull
      assertThat(actual!!.status).isEqualTo(HealthStatusType.UNHEALTHY)
      assertThat(actual.lastModified).isEqualTo(updatedLastModified)
    }
  }

  private fun assertTemplateVersionHealthStatusEqualsExpected(
    serviceConfigurationId: UUID,
    expected: TemplateVersionHealthStatus,
  ) {
    assertThat(templateVersionHealthStatusRepository.findByServiceConfigurationId(serviceConfigurationId))
      .isEqualTo(expected)
  }

  private fun newTemplateVersionHealthStatus(
    serviceConfig: ServiceConfiguration = testServiceConfig,
    status: HealthStatusType = HealthStatusType.HEALTHY,
    lastModified: LocalDateTime = initialLastModified,
  ) = TemplateVersionHealthStatus(
    status = status,
    serviceConfiguration = serviceConfig,
    lastModified = lastModified,
  )
}
