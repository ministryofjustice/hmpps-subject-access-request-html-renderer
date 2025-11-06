package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequestEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import java.time.LocalDate
import java.util.UUID

data class RenderRequest(
  val id: UUID? = null,
  val nomisId: String? = null,
  val ndeliusId: String? = null,
  val dateFrom: LocalDate? = null,
  var dateTo: LocalDate? = null,
  val sarCaseReferenceNumber: String? = null,
  val serviceConfiguration: ServiceConfiguration,
) {
  constructor(request: RenderRequestEntity, serviceConfiguration: ServiceConfiguration) : this(
    id = request.id,
    nomisId = request.nomisId,
    ndeliusId = request.ndeliusId,
    dateFrom = request.dateFrom,
    dateTo = request.dateTo,
    sarCaseReferenceNumber = request.sarCaseReferenceNumber,
    serviceConfiguration = serviceConfiguration,
  )

  fun documentHtmlKey(): String = "$id/${serviceConfiguration.serviceName}.html"

  fun documentJsonKey(): String = "$id/${serviceConfiguration.serviceName}.json"

  fun documentAttachmentKey(
    attachmentNumber: Int,
    filename: String,
  ): String = "$id/${serviceConfiguration.serviceName}/attachments/$attachmentNumber-$filename"

  override fun toString(): String = "RenderRequest(id=$id, nomisId=$nomisId, ndeliusId=$ndeliusId, " +
    "dateFrom=$dateFrom, dateTo=$dateTo, sarCaseReferenceNumber=$sarCaseReferenceNumber, serviceConfigurationId=${serviceConfiguration.id})"
}
