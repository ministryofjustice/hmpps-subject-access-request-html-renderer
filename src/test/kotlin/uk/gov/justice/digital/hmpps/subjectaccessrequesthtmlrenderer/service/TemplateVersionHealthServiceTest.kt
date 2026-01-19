package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType.HEALTHY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionHealthStatusRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.TemplateVersionHealthService
import java.time.Instant

class TemplateVersionHealthServiceTest {

  private val serviceConfiguration: ServiceConfiguration = ServiceConfiguration()
  private val templateVersionHealthStatus: TemplateVersionHealthStatus = mock()

  private val templateVersionHealthStatusRepository: TemplateVersionHealthStatusRepository = mock()
  private val serviceConfigurationService: ServiceConfigurationService = mock()

  private val templateVersionHealthService: TemplateVersionHealthService = TemplateVersionHealthService(
    templateVersionHealthStatusRepository,
    serviceConfigurationService,
  )

  @Nested
  inner class UpdateHealthStatusIfChanged {

    @Test
    fun `should update template version health status`() {
      templateVersionHealthService.updateHealthStatusIfChanged(
        serviceConfiguration = serviceConfiguration,
        newStatus = HEALTHY,
      )

      verify(templateVersionHealthStatusRepository, times(1)).updateStatusWhenChanged(
        newStatus = eq(HEALTHY),
        lastModified = any<Instant>(),
        serviceConfigurationId = eq(serviceConfiguration.id),
      )
    }
  }

  @Nested
  inner class CreateServiceTemplateVersionHealthStatusIfNotExists {

    @Test
    fun `should not create template version health status if service template migrated flag is false`() {
      serviceConfigurationIsNotFound()

      templateVersionHealthService.ensureTemplateVersionHealthStatusExists(serviceConfiguration)

      verifyFindByIdAndEnabledAndTemplateMigratedIsCalled()
      verifyNoInteractions(templateVersionHealthStatusRepository)
    }

    @Test
    fun `should not create template version health status if entry already exists for service configuration Id`() {
      serviceConfigurationIsFound()
      templateVersionHealthStatusExists()

      templateVersionHealthService.ensureTemplateVersionHealthStatusExists(serviceConfiguration)

      verifyFindByIdAndEnabledAndTemplateMigratedIsCalled()
      verifyFindByServiceConfigurationIdIsCalled()
      verifyNoMoreInteractions(templateVersionHealthStatusRepository)
    }

    @Test
    fun `should create template version health status if entry does not already exists for service configuration Id`() {
      serviceConfigurationIsFound()
      templateVersionHealthStatusDoesNotExist()

      val healthStatusCaptor = argumentCaptor<TemplateVersionHealthStatus>()

      val start = Instant.now()
      templateVersionHealthService.ensureTemplateVersionHealthStatusExists(serviceConfiguration)

      verifyFindByIdAndEnabledAndTemplateMigratedIsCalled()
      verifyFindByServiceConfigurationIdIsCalled()

      verify(templateVersionHealthStatusRepository, times(1))
        .saveAndFlush(healthStatusCaptor.capture())

      assertThat(healthStatusCaptor.allValues).hasSize(1)

      val actual = healthStatusCaptor.firstValue
      assertThat(actual.status).isEqualTo(HEALTHY)
      assertThat(actual.serviceConfiguration).isEqualTo(serviceConfiguration)
      assertThat(actual.lastModified).isBetween(start, Instant.now())
    }
  }

  private fun serviceConfigurationIsNotFound() {
    whenever(
      serviceConfigurationService.findByIdAndEnabledAndTemplateMigrated(
        serviceConfiguration.id,
        enabled = true,
        templateMigrated = true,
      ),
    ).thenReturn(null)
  }

  private fun serviceConfigurationIsFound() {
    whenever(
      serviceConfigurationService.findByIdAndEnabledAndTemplateMigrated(
        serviceConfiguration.id,
        enabled = true,
        templateMigrated = true,
      ),
    ).thenReturn(serviceConfiguration)
  }

  private fun templateVersionHealthStatusExists() {
    whenever(templateVersionHealthStatusRepository.findByServiceConfigurationId(serviceConfiguration.id))
      .thenReturn(templateVersionHealthStatus)
  }

  private fun templateVersionHealthStatusDoesNotExist() {
    whenever(templateVersionHealthStatusRepository.findByServiceConfigurationId(serviceConfiguration.id))
      .thenReturn(null)
  }

  private fun verifyFindByIdAndEnabledAndTemplateMigratedIsCalled() {
    verify(serviceConfigurationService, times(1))
      .findByIdAndEnabledAndTemplateMigrated(serviceConfiguration.id, true, true)
  }

  private fun verifyFindByServiceConfigurationIdIsCalled() {
    verify(templateVersionHealthStatusRepository, times(1))
      .findByServiceConfigurationId(serviceConfiguration.id)
  }
}
