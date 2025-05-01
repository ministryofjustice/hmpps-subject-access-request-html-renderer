package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class RenderRequest(
  val id: UUID? = null,

  val nomisId: String? = null,

  val ndeliusId: String? = null,

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate? = null,

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  var dateTo: LocalDate? = null,

  val sarCaseReferenceNumber: String? = null,

  val serviceName: String? = null,

  val serviceLabel: String? = null,

  val serviceUrl: String? = null,
) {

  @JsonIgnore
  fun documentKey(): String = "$id/$serviceName.html"

  override fun toString(): String = "RenderRequest(id=$id, nomisId=$nomisId, ndeliusId=$ndeliusId, " +
    "dateFrom=$dateFrom, dateTo=$dateTo, sarCaseReferenceNumber=$sarCaseReferenceNumber, serviceName=$serviceName, " +
    "serviceLabel=$serviceLabel, serviceUrl=$serviceUrl)"
}
