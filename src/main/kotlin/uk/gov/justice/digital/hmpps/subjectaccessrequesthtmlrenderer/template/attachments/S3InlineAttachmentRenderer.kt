package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Options
import kotlinx.coroutines.runBlocking
import org.apache.tika.mime.MimeTypes
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.subjectaccessrequest.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.InlineAttachment
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers.Companion.NO_DATA_HELD
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.attachments.InlineAttachmentRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.documentstore.DocumentStore
import java.util.UUID

class S3InlineAttachmentRenderer(
  private val templateDataFetcherFacade: TemplateDataFetcherFacade,
  private val objectMapper: ObjectMapper,
  private val documentStore: DocumentStore,
): InlineAttachmentRenderer {

  private companion object {
    private val log = LoggerFactory.getLogger(S3InlineAttachmentRenderer::class.java)
  }

  override fun inlineAttachmentContent(
    input: Any?,
    options: Options,
  ): String? {
    if (input == null) {
      log.info("input is null or empty returning null")
      return null
    }

    val inlineAttachment = getInputAsInlineAttachment(input)
    if (inlineAttachment.url.isEmpty()) {
      log.info("inline attachment url is empty returning null")
      return null
    }

    if ("image" != MediaType.parseMediaType(inlineAttachment.contentType).type) {
      throw SubjectAccessRequestTemplatingException(
        "Inline attachment with content type ${inlineAttachment.contentType} not supported",
      )
    }

    val renderRequestInfo = options.context.get("render-request-info") as RenderRequestInfo
    val inlineAttachmentFile = getInlineAttachmentFile(inlineAttachment, renderRequestInfo)
    storeInlineAttachment(inlineAttachmentFile)
    return inlineAttachmentFile.getImageSrc()
  }

  private fun getInputAsInlineAttachment(input: Any?): InlineAttachment = try {
    objectMapper.convertValue(input, InlineAttachment::class.java)
  } catch (e: IllegalArgumentException) {
    throw SubjectAccessRequestTemplatingException("Could not convert object to inline attachment: ${e.message}")
  }

  private fun getInlineAttachmentFile(
    inlineAttachment: InlineAttachment,
    renderRequestInfo: RenderRequestInfo,
  ): InlineAttachmentFile {
    val attachmentData = templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo)
    val filename = "${UUID.randomUUID()}${getFileExtension(inlineAttachment.contentType)}"

    return InlineAttachmentFile(
      requestId = renderRequestInfo.id,
      serviceName = renderRequestInfo.serviceName,
      filename = filename,
      contentType = inlineAttachment.contentType,
      filesize = inlineAttachment.filesize?.toLong() ?: 0L,
      data = attachmentData,
    )
  }

  private fun getFileExtension(
    contentType: String,
  ): String = MimeTypes.getDefaultMimeTypes().forName(contentType).extension
    .takeIf { it.isNotEmpty() }
    ?: throw SubjectAccessRequestTemplatingException("unknown file extension for contentType: $contentType")

  private fun storeInlineAttachment(inlineAttachmentFile: InlineAttachmentFile) = runBlocking {
    // runBlocking is a workaround as we cannot make interface method suspended as it will break backwards compatability
    documentStore.addInlineAttachment(inlineAttachmentFile)
  }

  override fun inlineAttachment(
    input: Any?,
    height: Int?,
    width: Int?,
    options: Options,
  ): String {
    if (input == null) {
      return NO_DATA_HELD
    }
    return """<img src="${inlineAttachmentContent(input, options)}" alt="Inline attachment" height="$height" width="$width"/>"""
  }
}
