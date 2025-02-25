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

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  val dateFrom: LocalDate? = null,

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  var dateTo: LocalDate? = null,

  val sarCaseReferenceNumber: String? = null,

  val serviceName: String? = null,

  val serviceUrl: String? = null,
) {
  @JsonIgnore
  fun getCacheKey(): String = "${this.id}_${this.serviceName}" // TODO does the key need to be more specific?

  override fun toString(): String = "RenderRequest(id=$id, nomisId=$nomisId, ndeliusId=$ndeliusId, " +
    "dateFrom=$dateFrom, dateTo=$dateTo, sarCaseReferenceNumber=$sarCaseReferenceNumber, serviceName=$serviceName, " +
    "serviceUrl=$serviceUrl)"
}
