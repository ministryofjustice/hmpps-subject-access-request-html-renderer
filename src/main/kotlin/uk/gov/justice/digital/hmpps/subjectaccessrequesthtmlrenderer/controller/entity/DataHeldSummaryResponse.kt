package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

data class DataHeldSummaryResponse(
  val nomisId: String?,
  val ndeliusId: String?,
  val dataHeld: Boolean,
  val dataHeldSummary: List<ServiceSummary>,
)

data class ServiceSummary(
  val serviceName: String?,
  val dataHeld: Boolean,
)
