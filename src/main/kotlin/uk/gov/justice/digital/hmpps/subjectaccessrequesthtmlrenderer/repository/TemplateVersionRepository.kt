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
      "ORDER BY tv.createdAt DESC " +
      "LIMIT 1",
  )
  fun findLatestByServiceConfigurationIdAndFileHash(
    @Param("serviceConfigId") serviceConfigurationId: UUID,
    @Param("fileHash") fileHash: String,
  ): TemplateVersion?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Modifying
  @Query(
    "UPDATE TemplateVersion " +
      "SET status = :status, " +
      "publishedAt = :publishedAt " +
      "WHERE id = :id " +
      "AND version = :version " +
      "AND fileHash = :fileHash",
  )
  fun publishPendingTemplateVersionByIdAndVersionAndFileHash(
    @Param("id") id: UUID,
    @Param("version") version: Int,
    @Param("status") status: TemplateVersionStatus = TemplateVersionStatus.PUBLISHED,
    @Param("fileHash") fileHash: String,
    @Param("publishedAt") publishedAt: LocalDateTime = LocalDateTime.now(),
  ): Int
}
