package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.subjectaccessrequest.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.InlineAttachment
import java.util.UUID

class InlineAttachmentFileServiceTest {

  private val inlineAttachmentFileService = InlineAttachmentFileService()
  private val renderRequestInfo: RenderRequestInfo = RenderRequestInfo(
    id = UUID.randomUUID(),
    serviceName = "service-666",
  )
  private val attachmentData = UUID.randomUUID().toString().toByteArray()

  @Test
  fun `should throw exception if content type is invalid`() {
    val inlineAttachment = InlineAttachment(
      contentType = "content/unknown-type",
      url = "http://url",
      filesize = 100,
    )

    val actual = assertThrows<SubjectAccessRequestTemplatingException> {
      inlineAttachmentFileService.createInlineAttachmentFile(
        renderRequestInfo = renderRequestInfo,
        attachmentInfo = inlineAttachment,
        attachmentData = attachmentData,
      )
    }
    assertThat(actual.message).startsWith("unknown file extension for contentType: content/unknown-type")
  }

  @Test
  fun `should create expected InlineAttachmentFile`() {
    val inlineAttachment = InlineAttachment(
      contentType = "image/jpeg",
      url = "http://url",
      filesize = attachmentData.size,
    )

    val actual = inlineAttachmentFileService.createInlineAttachmentFile(
      renderRequestInfo = renderRequestInfo,
      attachmentInfo = inlineAttachment,
      attachmentData = attachmentData,
    )

    assertThat(actual.requestId).isEqualTo(renderRequestInfo.id)
    assertThat(actual.serviceName).isEqualTo(renderRequestInfo.serviceName)
    assertThat(actual.contentType).isEqualTo("image/jpeg")
    assertThat(actual.filesize).isEqualTo(attachmentData.size.toLong())
    assertThat(actual.data).isEqualTo(attachmentData)
    assertThat(actual.documentKey)
      .isEqualTo("${renderRequestInfo.id}/${renderRequestInfo.serviceName}/inline-attachments/${actual.filename}")
    assertThat(actual.getImageSrc())
      .matches("^https://sar-inline-attachments/${renderRequestInfo.id}/${renderRequestInfo.serviceName}/inline-attachments/[0-9a-zA-Z-]+.jpg$")
  }
}
