package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.SubjectDataHeldRequest
import java.util.UUID

@Service
class DataHeldService(
  private val dynamicServicesClient: DynamicServicesClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun isSubjectDataHeld(request: SubjectDataHeldRequest): Boolean {
    log.info(
      "checking service={} for data held on nomisId:{} / ndeliusId:{}",
      request.service!!.name,
      request.nomisId,
      request.ndeliusId,
    )

    val response = dynamicServicesClient.getSubjectAccessRequestData(
      RenderRequest(
        id = UUID.randomUUID(),
        serviceUrl = request.service.url,
        serviceName = request.service.name,
        nomisId = request.nomisId,
        ndeliusId = request.ndeliusId,
        dateFrom = request.dateFrom,
        dateTo = request.dateTo,
      ),
    )

    return response?.statusCode == HttpStatus.OK.also {
      log.info(
        "data held={} for service={}, nomisId:{} / ndeliusId:{} ",
        it,
        request.service.name,
        request.nomisId,
        request.ndeliusId,
      )
    }
  }
}
