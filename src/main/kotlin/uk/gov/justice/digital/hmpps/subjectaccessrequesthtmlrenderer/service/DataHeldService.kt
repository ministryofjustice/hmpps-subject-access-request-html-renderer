package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.BacklogController.SubjectDataHeldRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.util.UUID

@Service
class DataHeldService(
  private val dynamicServicesClient: DynamicServicesClient,
) {

  fun isSubjectDataHeld(request: SubjectDataHeldRequest): Boolean {
    val response = dynamicServicesClient.getSubjectAccessRequestData(
      RenderRequest(
        id = UUID.randomUUID(),
        serviceUrl = request.serviceUrl,
        serviceName = request.serviceName,
        nomisId = request.nomisId,
        ndeliusId = request.ndeliusId,
        dateFrom = request.dateFrom,
        dateTo = request.dateTo,
      ),
    )

    return response?.statusCode == HttpStatus.OK
  }
}
