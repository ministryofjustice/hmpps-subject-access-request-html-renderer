package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateService
import java.util.UUID

@Service
class RenderService(
  val dynamicServicesClient: DynamicServicesClient,
  val documentStore: DocumentStore,
  val templateService: TemplateService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest): String {
    val documentKey = renderRequest.documentKey()

    if (documentStore.contains(documentKey = documentKey)) {
      log.info("document for request: ${renderRequest.id} exists no action required")
      return documentKey
    }

    val getDataResponse: ResponseEntity<Map<*, *>> = getData(renderRequest)
    log.info("get data response status:  ${getDataResponse.statusCode}")

    val content = getDataResponse.body["content"]

    // TODO handle scenario where template not found.
    // Need to write data as YAML.
    val renderedData = templateService.renderServiceDataHtml(renderRequest, content)
    documentStore.add(renderRequest, renderedData?.toByteArray())

    return documentKey
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  fun renderServiceDataHtmlForDev(serviceName: String, data: Map<*, *>): String = String(
    templateService.renderServiceDataHtml(
      RenderRequest(
        serviceName = serviceName,
      ),
      data["content"],
    )!!.toByteArray(),
  )

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  private fun getData(renderRequest: RenderRequest): ResponseEntity<Map<*, *>> = dynamicServicesClient.getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
    message = "API response data was null",
    cause = null,
    subjectAccessRequestId = renderRequest.id,
    params = mapOf("serviceUrl" to renderRequest.serviceUrl),
  )
}
