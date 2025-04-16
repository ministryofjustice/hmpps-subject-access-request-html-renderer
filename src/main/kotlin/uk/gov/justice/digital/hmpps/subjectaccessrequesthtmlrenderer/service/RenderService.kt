package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateService
import java.io.ByteArrayOutputStream
import java.util.UUID

@Service
class RenderService(
  private val dynamicServicesClient: DynamicServicesClient,
  private val documentStore: DocumentStore,
  private val templateService: TemplateService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest): RenderResult {
    log.info("rendering html for request: {}, serviceName: {}", renderRequest.id, renderRequest.serviceName)
    val documentKey = renderRequest.documentKey()

    if (documentStore.contains(documentKey = documentKey)) {
      log.info("document for request: $documentKey exists no action required")
      return RenderResult.DATA_ALREADY_EXISTS
    }

    val content = getDataForSubject(renderRequest)

    val renderedData = templateService.renderServiceDataHtml(renderRequest, content)
    storeRenderedHtml(renderRequest, renderedData)

    log.info("document $documentKey created and added to document store")
    return RenderResult.CREATED
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  private fun getDataForSubject(renderRequest: RenderRequest): Any? {
    telemetryClient.renderEvent(GET_SERVICE_DATA, renderRequest)

    val response: ResponseEntity<Map<*, *>> = dynamicServicesClient
      .getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
      message = "API response data was null",
      cause = null,
      subjectAccessRequestId = renderRequest.id,
      params = mapOf("serviceUrl" to renderRequest.serviceUrl),
    )

    log.info("get data response status:  ${response.statusCode}")

    return when (response.statusCode) {
      HttpStatus.OK -> {
        telemetryClient.renderEvent(SERVICE_DATA_RETURNED, renderRequest)
        response.body["content"]
      }
      HttpStatus.NO_CONTENT -> {
        telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT, renderRequest)
        null
      }
      else -> throw SubjectAccessRequestException(
        message = "get service data returned unexpected response status",
        cause = null,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf(
          "status" to response.statusCode,
          "serviceName" to renderRequest.serviceName,
          "serviceUrl" to renderRequest.serviceUrl,
        ),
      )
    }
  }

  private suspend fun storeRenderedHtml(renderRequest: RenderRequest, renderedData: ByteArrayOutputStream?) {
    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_STARTED, renderRequest)
    documentStore.add(renderRequest, renderedData?.toByteArray())
    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_COMPLETED, renderRequest)
  }

  enum class RenderResult {
    /**
     * Service data was rendered to HTML and added to the document store.
     */
    CREATED,

    /**
     * Document store contains existing entry for subject access request ID/service name combination. No action required
     */
    DATA_ALREADY_EXISTS,
  }
}
