package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.Attachment
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.ServiceData
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.GET_SERVICE_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_DATA_RETURNED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.STORE_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.documentstore.DocumentStore
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.RenderedHtml
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.TemplateRenderingService
import java.util.UUID

@Service
class RenderService(
  private val dynamicServicesClient: DynamicServicesClient,
  private val documentStore: DocumentStore,
  private val templateRenderingService: TemplateRenderingService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED = 209
  }

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest) {
    log.info(
      "rendering html for request: {}, serviceName: {}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    val (content, attachments) = getContentAndAttachmentsFromJsonData(renderRequest)
    val renderedHtml = renderServiceDataHtml(renderRequest, content)
    storeRenderedHtml(renderRequest, renderedHtml)

    attachments?.forEach { attachment -> getAndStoreAttachment(renderRequest, attachment) }
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  suspend fun listAllFilesForId(subjectAccessRequestId: UUID) = documentStore.listAll(subjectAccessRequestId)

  private suspend fun getContentAndAttachmentsFromJsonData(
    renderRequest: RenderRequest,
  ): ServiceData {
    telemetryClient.renderEvent(GET_SERVICE_DATA, renderRequest)
    log.info(
      "retrieving service data for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    try {
      val response: ResponseEntity<ServiceData> = dynamicServicesClient.getSubjectAccessRequestData(renderRequest)
        ?: throw SubjectAccessRequestException(
          message = "api response data was null",
          cause = null,
          errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
          subjectAccessRequestId = renderRequest.id,
          params = mapOf("serviceUrl" to renderRequest.serviceConfiguration.url),
        )

      log.info("get {} data response status: {}", renderRequest.serviceConfiguration.serviceName, response.statusCode)
      return extractResponseBody(response, renderRequest).sanitize()
    } catch (ex: Exception) {
      if (ex is SubjectAccessRequestException) {
        throw ex
      }

      throw SubjectAccessRequestException(
        message = "get data request failed with exception",
        cause = ex,
        errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
        subjectAccessRequestId = renderRequest.id,
        params = mapOf("serviceUrl" to renderRequest.serviceConfiguration.url),
      )
    }
  }

  private fun renderServiceDataHtml(
    renderRequest: RenderRequest,
    content: Any?,
  ): RenderedHtml = templateRenderingService.renderServiceDataHtml(renderRequest, content)

  private suspend fun getAndStoreAttachment(renderRequest: RenderRequest, attachment: Attachment) {
    val documentAttachmentKey = renderRequest.documentAttachmentKey(attachment.attachmentNumber, attachment.filename)
    telemetryClient.renderEvent(GET_ATTACHMENT_STARTED, renderRequest)
    log.info(
      "getting attachment: ${attachment.filename} for request: {}",
      documentAttachmentKey,
      renderRequest.serviceConfiguration.serviceName,
    )
    val attachmentData = dynamicServicesClient.getAttachment(
      renderRequest,
      attachment.url,
      attachment.contentType,
      attachment.filesize,
    )
    telemetryClient.renderEvent(GET_ATTACHMENT_COMPLETE, renderRequest)
    storeAttachment(renderRequest, attachment, attachmentData)
  }

  private fun extractResponseBody(
    response: ResponseEntity<ServiceData>,
    renderRequest: RenderRequest,
  ): ServiceData = when (response.statusCode.value()) {
    HttpStatus.OK.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_RETURNED, renderRequest)
      response.body.also {
        log.info(
          "received response body, id: {}, service: {}",
          renderRequest.id,
          renderRequest.serviceConfiguration.serviceName,
        )
      } ?: ServiceData().also {
        log.warn(
          "received null response body, id: {}, service: {}",
          renderRequest.id,
          renderRequest.serviceConfiguration.serviceName,
        )
      }
    }

    HttpStatus.NO_CONTENT.value() -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT, renderRequest)
      ServiceData()
    }

    STATUS_IDENTIFIER_TYPE_NOT_SUPPORTED -> {
      telemetryClient.renderEvent(SERVICE_DATA_NO_CONTENT_ID_NOT_SUPPORTED, renderRequest)
      ServiceData()
    }

    else -> throw SubjectAccessRequestException(
      message = "get service data returned unexpected response status",
      cause = null,
      errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
      subjectAccessRequestId = renderRequest.id,
      params = mapOf(
        "status" to response.statusCode,
        "serviceName" to renderRequest.serviceConfiguration.serviceName,
        "serviceUrl" to renderRequest.serviceConfiguration.url,
      ),
    )
  }

  private suspend fun storeRenderedHtml(
    renderRequest: RenderRequest,
    renderedHtml: RenderedHtml,
  ) {
    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_STARTED, renderRequest)
    log.info(
      "stored rendered html document for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    documentStore.addHtml(renderRequest, renderedHtml)

    telemetryClient.renderEvent(RenderEvent.STORE_RENDERED_HTML_COMPLETED, renderRequest)
    log.info(
      "html document stored successfully for id={}, service={}",
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
  }

  private suspend fun storeAttachment(renderRequest: RenderRequest, attachment: Attachment, attachmentData: ByteArray) {
    telemetryClient.renderEvent(STORE_ATTACHMENT_STARTED, renderRequest)
    log.info(
      "storing attachment {} for id={}, service={}",
      attachment.attachmentNumber,
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )

    documentStore.addAttachment(renderRequest, attachment, attachmentData)

    telemetryClient.renderEvent(STORE_ATTACHMENT_COMPLETED, renderRequest)
    log.info(
      "attachment {} stored successfully for id={}, service={}",
      attachment.attachmentNumber,
      renderRequest.id,
      renderRequest.serviceConfiguration.serviceName,
    )
  }
}
