package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException

@Service
class RenderService(
  val dynamicServicesClient: DynamicServicesClient,
  val cache: CacheService,
  val templateService: TemplateService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun renderServiceDataHtml(renderRequest: RenderRequest): String {
    if (cache.contains(renderRequest.getCacheKey())) {
      log.info("cached data exists for request: ${renderRequest.id} no action required")
      return renderRequest.getCacheKey()
    }

    val getDataResponse: ResponseEntity<Map<*, *>> = getData(renderRequest)
    log.info("get data response status:  ${getDataResponse.statusCode}")

    val content = getDataResponse.body["content"]

    // TODO handle scenario where template not found.
    // Need to write data as YAML.
    val renderedData = templateService.renderServiceDataHtml(renderRequest, content)
    cache.add(renderRequest.getCacheKey(), renderedData)

    return renderRequest.getCacheKey()
  }

  fun renderServiceDataHtmlForDev(serviceName: String, data: Map<*, *>): String = String(
    templateService.renderServiceDataHtml(
      RenderRequest(
        serviceName = serviceName,
      ),
      data["content"],
    )!!.toByteArray(),
  )

  private fun getData(renderRequest: RenderRequest): ResponseEntity<Map<*, *>> = dynamicServicesClient.getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
    message = "API response data was null",
    cause = null,
    subjectAccessRequestId = renderRequest.id,
    params = mapOf("serviceUrl" to renderRequest.serviceUrl),
  )
}
