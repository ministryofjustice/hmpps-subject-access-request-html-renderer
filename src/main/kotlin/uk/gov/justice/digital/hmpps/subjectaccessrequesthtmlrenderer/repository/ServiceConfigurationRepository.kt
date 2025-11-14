package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import java.util.UUID

@Repository
interface ServiceConfigurationRepository : JpaRepository<ServiceConfiguration, UUID> {
  fun findByServiceName(serviceName: String): ServiceConfiguration?

  fun findByIdAndEnabledAndTemplateMigrated(
    id: UUID,
    enabled: Boolean = true,
    templateMigrated: Boolean = true,
  ): ServiceConfiguration?
}
