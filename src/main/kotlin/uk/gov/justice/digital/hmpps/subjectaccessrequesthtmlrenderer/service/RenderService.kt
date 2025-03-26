package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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

  suspend fun renderServiceDataHtml(renderRequest: RenderRequest): RenderResult {
    val documentKey = renderRequest.documentKey()

    if (documentStore.contains(documentKey = documentKey)) {
      log.info("document for request: $documentKey exists no action required")
      return RenderResult.DATA_ALREADY_EXISTS
    }

    val content = getDataForSubject(renderRequest)

    val renderedData = templateService.renderServiceDataHtml(renderRequest, content)
    documentStore.add(renderRequest, renderedData?.toByteArray())

    log.info("document $documentKey created and added to document store")
    return RenderResult.CREATED
  }

  suspend fun getRenderedHtml(documentKey: String): ByteArray? = documentStore.getByDocumentKey(documentKey)

  suspend fun listCacheFilesWithPrefix(subjectAccessRequestId: UUID) = documentStore.list(subjectAccessRequestId)

  private fun getDataForSubject(renderRequest: RenderRequest): List<Any> {
    val response: ResponseEntity<Map<*, *>> = dynamicServicesClient
      .getSubjectAccessRequestData(renderRequest) ?: throw SubjectAccessRequestException(
      message = "API response data was null",
      cause = null,
      subjectAccessRequestId = renderRequest.id,
      params = mapOf("serviceUrl" to renderRequest.serviceUrl),
    )

    log.info("get data response status:  ${response.statusCode}")

    return when (response.statusCode) {
      HttpStatus.OK -> response.body["content"] as ArrayList<*>
      HttpStatus.NO_CONTENT -> emptyList()
      else -> throw RuntimeException("todo")
    }
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
