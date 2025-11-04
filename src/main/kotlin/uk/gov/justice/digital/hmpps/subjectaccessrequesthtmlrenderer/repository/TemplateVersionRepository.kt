package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import java.util.UUID

@Repository
interface TemplateVersionRepository : JpaRepository<TemplateVersion, UUID>
