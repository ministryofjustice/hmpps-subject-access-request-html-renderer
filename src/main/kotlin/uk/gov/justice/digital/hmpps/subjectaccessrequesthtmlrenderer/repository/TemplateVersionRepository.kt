package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
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
    "SELECT tv FROM TemplateVersion tv " +
      "WHERE tv.serviceConfiguration.id = :serviceConfigId " +
      "AND tv.fileHash = :fileHash " +
      "AND tv.status = :status " +
      "ORDER BY tv.publishedAt DESC " +
      "LIMIT 1",
  )
  fun findLatestByServiceConfigurationIdAndStatusAndFileHash(
    @Param("serviceConfigId") serviceConfigurationId: UUID,
    @Param("status") status: TemplateVersionStatus,
    @Param("fileHash") fileHash: String,
  ): TemplateVersion?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Modifying
  @Query(
    "UPDATE TemplateVersion " +
      "SET status = :newStatus, " +
      "publishedAt = :publishedAt " +
      "WHERE id = :id " +
      "AND version = :version " +
      "AND status = :oldStatus",
  )
  fun updateStatusAndPublishedAtByIdAndVersion(
    @Param("id") id: UUID,
    @Param("version") version: Int,
    @Param("newStatus") newStatus: TemplateVersionStatus,
    @Param("oldStatus") oldStatus: TemplateVersionStatus,
    @Param("publishedAt") publishedAt: LocalDateTime = LocalDateTime.now(),
  ): Int
}
