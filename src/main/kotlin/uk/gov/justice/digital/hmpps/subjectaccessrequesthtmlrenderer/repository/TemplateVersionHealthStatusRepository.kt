package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionHealthStatus
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface TemplateVersionHealthStatusRepository : JpaRepository<TemplateVersionHealthStatus, UUID> {

  fun findByServiceConfigurationId(serviceConfigurationId: UUID): TemplateVersionHealthStatus?

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
    "UPDATE TemplateVersionHealthStatus t " +
      "SET t.status =:newStatus, t.lastModified = :lastModified " +
      "WHERE t.serviceConfiguration.id = :serviceConfigurationId " +
      "AND t.status != :newStatus",
  )
  fun updateStatusWhenChanged(
    @Param("newStatus") newStatus: HealthStatusType,
    @Param("lastModified") lastModified: LocalDateTime = LocalDateTime.now(),
    @Param("serviceConfigurationId") serviceConfigurationId: UUID,
  ): Int
}
