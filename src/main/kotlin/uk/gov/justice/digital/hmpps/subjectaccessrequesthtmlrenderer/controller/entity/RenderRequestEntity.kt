package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class RenderRequestEntity(
  val id: UUID? = null,

  val nomisId: String? = null,

  val ndeliusId: String? = null,

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate? = null,

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  var dateTo: LocalDate? = null,

  val sarCaseReferenceNumber: String? = null,

  val serviceConfigurationId: UUID? = null,

)
