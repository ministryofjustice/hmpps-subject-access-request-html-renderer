package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import java.util.UUID

@Repository
interface TemplateVersionRepository : JpaRepository<TemplateVersion, UUID> {

  @Query(
    "SELECT tv FROM TemplateVersion tv " +
      "WHERE tv.serviceConfiguration.id = :serviceConfigId " +
      "AND tv.fileHash = :fileHash " +
      "ORDER BY tv.createdAt DESC, tv.version DESC " +
      "LIMIT 1",
  )
  fun findLatestByServiceConfigurationIdAndFileHash(
    @Param("serviceConfigId") serviceConfigurationId: UUID,
    @Param("fileHash") fileHash: String,
  ): TemplateVersion?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
    @Param("id") id: UUID,
    @Param("version") version: Int,
    @Param("fileHash") fileHash: String,
    @Param("status") status: TemplateVersionStatus = TemplateVersionStatus.PENDING,
  ): TemplateVersion?
}
