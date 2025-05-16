package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.DataHeldSummaryRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.ServiceSummary
import java.util.UUID

@Service
class DataHeldService(
  private val dynamicServicesClient: DynamicServicesClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getDataHeldSummary(request: DataHeldSummaryRequest): List<ServiceSummary> {
    val summary: MutableList<ServiceSummary> = mutableListOf()

    request.services.forEach { service ->

      log.info(
        "checking service={} for data held on nomisId:{} / ndeliusId:{}",
        service.name,
        request.nomisId,
        request.ndeliusId,
      )

      val response = dynamicServicesClient.getSubjectAccessRequestData(
        RenderRequest(
          id = UUID.randomUUID(),
          serviceUrl = service.url,
          serviceName = service.name,
          nomisId = request.nomisId,
          ndeliusId = request.ndeliusId,
          dateFrom = request.dateFrom,
          dateTo = request.dateTo,
        ),
      )

      val dataHeld = response?.statusCode == HttpStatus.OK

      summary.add(
        ServiceSummary(
          serviceName = service.name,
          dataHeld = dataHeld,
        ),
      ).also {
        log.info(
          "data held={} for service={}, nomisId:{} / ndeliusId:{} ",
          dataHeld,
          service.name,
          request.nomisId,
          request.ndeliusId,
        )
      }
    }

    return summary
  }
}
