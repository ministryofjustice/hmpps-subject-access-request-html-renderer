package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.LocationDetail

@Repository
interface LocationDetailsRepository : JpaRepository<LocationDetail, String> {
  fun findByDpsId(dpsId: String): LocationDetail?
  fun findByNomisId(nomisId: Int): LocationDetail?
}
