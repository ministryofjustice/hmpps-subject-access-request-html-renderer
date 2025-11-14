package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.backlog

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.BacklogController.SubjectDataHeldRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.util.UUID

@Service
class DataHeldService(
  private val dynamicServicesClient: DynamicServicesClient,
  private val serviceConfigurationService: ServiceConfigurationService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(DataHeldService::class.java)
  }

  fun isSubjectDataHeld(request: SubjectDataHeldRequest): Boolean {
    log.info("executing subject data held summary request for: subject: {}", request.nomisId ?: request.ndeliusId)

    val serviceConfiguration = serviceConfigurationService.findByServiceNameOrNull(request.serviceName!!)
      ?: throw RuntimeException("Service configuration for ${request.serviceName} not found")

    val response = dynamicServicesClient.getSubjectAccessRequestData(
      RenderRequest(
        id = UUID.randomUUID(),
        nomisId = request.nomisId,
        ndeliusId = request.ndeliusId,
        dateFrom = request.dateFrom,
        dateTo = request.dateTo,
        serviceConfiguration = serviceConfiguration,
      ),
    )

    return (response?.statusCode == HttpStatus.OK).also {
      log.info(
        "subject data held summary request completed: subject: {}, data held: {}",
        request.nomisId ?: request.ndeliusId,
        it,
      )
    }
  }
}
