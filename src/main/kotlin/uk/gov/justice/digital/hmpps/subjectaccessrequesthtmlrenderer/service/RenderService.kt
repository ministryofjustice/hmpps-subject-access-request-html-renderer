package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateService
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.String
import kotlin.collections.get

@Service
class RenderService(
  private val dynamicServicesClient: DynamicServicesClient,
  private val documentStore: DocumentStore,
  private val templateService: TemplateService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED = 209
  }

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest): RenderResult {
    log.info("rendering html for request: {}, serviceName: {}", renderRequest.id, renderRequest.serviceName)
    val documentJsonKey = renderRequest.documentJsonKey()

    val (data, attachments) =
    if (documentStore.contains(documentJsonKey)) {
      log.info("json response for request: $documentJsonKey exists no action required")
      val json = documentStore.getByDocumentKey(documentJsonKey)
      val jsonMap = objectMapper.readValue(json, Map::class.java)
      Pair(jsonMap["content"], getAttachmentsFromJson(jsonMap))
    } else {
      log.info("json response for request: $documentJsonKey does not exist getting data from ${renderRequest.serviceName}")
      getDataForSubject(renderRequest)
    }

    val documentHtmlKey = renderRequest.documentHtmlKey()
    if (documentStore.contains(documentKey = documentHtmlKey)) {
      log.info("document html for request: $documentHtmlKey exists no action required")
    } else {
      log.info("document html for request: $documentHtmlKey does not exist rendering for ${renderRequest.serviceName}")
      val renderedData = templateService.renderServiceDataHtml(renderRequest, data)
      storeRenderedHtml(renderRequest, renderedData)
    }

    attachments?.forEach { attachment ->
      val documentAttachmentKey = renderRequest.documentAttachmentKey(attachment.filename)
      if (documentStore.contains(documentKey = documentAttachmentKey)) {
        log.info("attachment for request: $documentAttachmentKey exists no action required")
      } else {
        log.info("document html for request: $documentAttachmentKey does not exist downloading ${attachment.filename} for ${renderRequest.serviceName}")
        val attachmentData = dynamicServicesClient.getAttachment(attachment.url, attachment.contentType)
        if (attachmentData.size.toLong() != attachment.filesize) {
          throw SubjectAccessRequestException(
            subjectAccessRequestId = renderRequest.id,
            message = "Attachment ${attachment.filename} filesize ${attachment.filesize} does not match retrieved data size ${attachmentData.size}"
          )
        }
        documentStore.addAttachment(renderRequest, attachment, attachmentData)
      }
    }

    log.info("document $documentHtmlKey created and added to document store")
    return RenderResult.CREATED
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  private fun getDataForSubject(renderRequest: RenderRequest): Pair<Any?, List<Attachment>?> {
    telemetryClient.renderEvent(GET_SERVICE_DATA, renderRequest)
    log.info("Retrieved service data for id={}, service={}", renderRequest.id, renderRequest.serviceName)

    try {
      val response: ResponseEntity<Map<*, *>> = dynamicServicesClient
        .getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
        message = "API response data was null",
        cause = null,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceUrl" to renderRequest.serviceUrl),
      )

      log.info("get data response status:  ${response.statusCode}")

      val content = extractResponseContent(response, renderRequest)
      val attachments = extractResponseAttachments(response)
      return Pair(content, attachments)
    } catch (ex: Exception) {
      if (ex is SubjectAccessRequestException) {
        throw ex
      }

      throw SubjectAccessRequestException(
        message = "get data request failed with exception",
        cause = ex,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceUrl" to renderRequest.serviceUrl),
      )
    }
  }

  private fun extractResponseContent(
    response: ResponseEntity<Map<*, *>>,
    renderRequest: RenderRequest,
  ): Any? = when (response.statusCode.value()) {
    HttpStatus.OK.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_RETURNED, renderRequest)
      response.body["content"].also {
        log.info(
          "extracted service data from response body, id: {}, service: {}",
          renderRequest.id,
          renderRequest.serviceName,
        )
      }
    }

    HttpStatus.NO_CONTENT.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT, renderRequest)
      null
    }

    STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED, renderRequest)
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

  private fun extractResponseAttachments(response: ResponseEntity<Map<*, *>>): List<Attachment>? = when (response.statusCode.value()) {
    HttpStatus.OK.value() -> getAttachmentsFromJson(response.body)

    HttpStatus.NO_CONTENT.value() -> null

    STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED -> null

    else -> throw SubjectAccessRequestException(
      message = "get service data returned unexpected response status",
      cause = null,
    )
  }

  private fun getAttachmentsFromJson(jsonBody: Map<*, *>): List<Attachment>? {
    val attachments = jsonBody.get("attachments") ?: emptyList<Map<String, Any>>()
    return (attachments  as List<Map<String, Any>>).mapNotNull { attachment: Map<String, Any> ->
      mapAttachment(attachment)
    }
  }

  private fun mapAttachment(attachmentMap: Map<String, Any>) = Attachment(
    attachmentRef = (attachmentMap["attachmentRef"] as String),
    name = attachmentMap["name"] as String,
    contentType =  attachmentMap["contentType"] as String,
    url =  attachmentMap["url"] as String,
    filesize =  (attachmentMap["filesize"] as Integer).toLong(),
    filename =  attachmentMap["filename"] as String,
  )

  private suspend fun storeRenderedHtml(renderRequest: RenderRequest, renderedData: ByteArrayOutputStream?) {
    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_STARTED, renderRequest)
    log.info("stored rendered html document for id={}, service={}", renderRequest.id, renderRequest.serviceName)

    documentStore.add(renderRequest, renderedData?.toByteArray())

    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_COMPLETED, renderRequest)
    log.info("html document stored successfully for id={}, service={}", renderRequest.id, renderRequest.serviceName)
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

data class Attachment (
  val attachmentRef: String,
  val name: String,
  val contentType: String,
  val url: String,
  val filesize: Long,
  val filename: String,
)
