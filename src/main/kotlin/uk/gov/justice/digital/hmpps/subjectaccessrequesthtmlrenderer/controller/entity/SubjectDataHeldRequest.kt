package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import java.time.LocalDate

data class SubjectDataHeldRequest(
  val nomisId: String? = null,
  val ndeliusId: String? = null,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
  val service: DpsService? = null,
) {
  constructor() : this("", "", null, null, null)
}

open class DpsService(val name: String, val url: String) {
  constructor() : this("", "")
}
