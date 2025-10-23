package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceTemplateMetadata
import java.util.UUID

@Repository
interface ServiceTemplateMetadataRepository: JpaRepository<ServiceTemplateMetadata, UUID> {

  @Query(
    "SELECT template FROM ServiceTemplateMetadata template " +
    "WHERE template.serviceConfiguration.serviceName = :serviceName " +
    "ORDER BY template.version DESC " +
    "LIMIT 1"
  )
  fun findLatestByServiceName(@Param("serviceName") serviceName: String): ServiceTemplateMetadata?
}
