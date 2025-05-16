package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import java.time.LocalDate

data class DataHeldSummaryRequest(
  val nomisId: String? = null,
  val ndeliusId: String? = null,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
  val services: List<DpsService>,
) {
  constructor() : this("", "", null, null, emptyList())
}

open class DpsService(val name: String, val url: String) {
  constructor() : this("", "")
}
