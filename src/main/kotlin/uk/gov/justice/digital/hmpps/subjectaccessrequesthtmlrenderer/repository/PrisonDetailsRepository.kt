package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail

@Repository
interface PrisonDetailsRepository : JpaRepository<PrisonDetail, String> {
  fun findByPrisonId(prisonId: String): PrisonDetail?
}
