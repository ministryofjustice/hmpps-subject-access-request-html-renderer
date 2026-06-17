package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface TemplateVersionRepository : JpaRepository<TemplateVersion, UUID> {

  @Query(
    """
      SELECT tv FROM TemplateVersion tv
      WHERE tv.serviceConfiguration.id = :serviceConfigId
      AND tv.status = 'PUBLISHED' 
      ORDER BY tv.createdAt DESC, tv.version DESC LIMIT 1
    """,
  )
  fun findLatestPublishedByServiceConfigurationId(serviceConfigId: UUID): TemplateVersion?

  /**
   * Get the latest PENDING version created after the latest PUBLISHED version, OR the latest PENDING version if no
   * PUBLISHED version exists.
   */
  @Query(
    """
    SELECT v FROM TemplateVersion v
    WHERE v.serviceConfiguration.id = :serviceConfigId
    AND v.status = 'PENDING'
    AND v.createdAt > COALESCE(
        (
            SELECT MAX(v2.createdAt) 
            FROM TemplateVersion v2
            WHERE v2.serviceConfiguration.id = :serviceConfigId
            AND v2.status = 'PUBLISHED'
            
        ),
        :defaultCreatedAt
    )
    ORDER BY v.createdAt DESC
  """,
  )
  fun findLatestPendingByServiceConfigurationId(
    @Param("serviceConfigId") serviceConfigId: UUID,
    @Param("defaultCreatedAt") defaultCreatedAt: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0, 0),
  ): TemplateVersion?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
    @Param("id") id: UUID,
    @Param("version") version: Int,
    @Param("fileHash") fileHash: String,
    @Param("status") status: TemplateVersionStatus = TemplateVersionStatus.PENDING,
  ): TemplateVersion?

  fun findTemplateVersionByServiceConfigurationId(serviceConfigurationId: UUID): List<TemplateVersion>
}
