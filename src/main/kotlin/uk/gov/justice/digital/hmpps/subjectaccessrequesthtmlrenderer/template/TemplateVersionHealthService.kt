package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionHealthStatusRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.time.LocalDateTime

@Service
class TemplateVersionHealthService(
  private val templateVersionHealthStatusRepository: TemplateVersionHealthStatusRepository,
  private val serviceConfigurationService: ServiceConfigurationService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * Create a new [TemplateVersionHealthStatus] for the specified service if it does not already exist.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun ensureTemplateVersionHealthStatusExists(serviceConfiguration: ServiceConfiguration) {
    serviceConfigurationService.findByIdAndEnabledAndTemplateMigrated(
      id = serviceConfiguration.id,
      templateMigrated = true,
      enabled = true,
    ) ?: return

    templateVersionHealthStatusRepository.findByServiceConfigurationId(serviceConfiguration.id) ?: run {
      log.info("creating new templateVersionHealthStatus record for {}", serviceConfiguration.serviceName)

      templateVersionHealthStatusRepository.saveAndFlush(
        TemplateVersionHealthStatus(
          serviceConfiguration = serviceConfiguration,
          lastModified = LocalDateTime.now(),
          status = HealthStatusType.HEALTHY,
        ),
      )
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateHealthStatusIfChanged(serviceConfiguration: ServiceConfiguration, newStatus: HealthStatusType) {
    templateVersionHealthStatusRepository.updateStatusWhenChanged(
      newStatus = newStatus,
      serviceConfigurationId = serviceConfiguration.id,
    )
  }
}
