package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import java.time.LocalDate

data class GetSubjectAccessRequestDataParams(
  val prn: String? = null,
  val crn: String? = null,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
)
