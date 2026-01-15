package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionHealthStatusRepository
import java.time.LocalDateTime

@Service
class TemplateVersionHealthService(
  private val templateVersionHealthStatusRepository: TemplateVersionHealthStatusRepository,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * Create a new [TemplateVersionHealthStatus] for the specified service if it does not already exist.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun createServiceTemplateVersionHealthStatusIfNotExists(serviceConfiguration: ServiceConfiguration) {
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

  /**
   * Set the Service Template Version Health Status to [HealthStatusType.UNHEALTHY] if it is not already. No update
   * applied if the status is already [HealthStatusType.UNHEALTHY].
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun markAsUnhealthyIfNotAlready(serviceConfiguration: ServiceConfiguration) {
    templateVersionHealthStatusRepository.updateStatusWhenChanged(
      newStatus = HealthStatusType.UNHEALTHY,
      serviceConfigurationId = serviceConfiguration.id,
      currentStatus = HealthStatusType.HEALTHY,
    )
  }

  /**
   * Set the Service Template Version Health Status to [HealthStatusType.HEALTHY] if it is not already. No update
   * applied if the status is already [HealthStatusType.HEALTHY].
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun markAsHealthyIfNotAlready(serviceConfiguration: ServiceConfiguration) {
    templateVersionHealthStatusRepository.updateStatusWhenChanged(
      newStatus = HealthStatusType.HEALTHY,
      serviceConfigurationId = serviceConfiguration.id,
      currentStatus = HealthStatusType.UNHEALTHY,
    )
  }
}
