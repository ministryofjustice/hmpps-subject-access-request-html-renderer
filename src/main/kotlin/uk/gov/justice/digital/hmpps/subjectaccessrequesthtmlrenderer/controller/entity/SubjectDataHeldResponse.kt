package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

data class SubjectDataHeldResponse(
  val nomisId: String?,
  val ndeliusId: String?,
  val dataHeld: Boolean,
  val serviceName: String?,
)
