package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail

@Repository
interface UserDetailsRepository : JpaRepository<UserDetail, String> {
  fun findByUsername(prisonId: String): UserDetail?
}
