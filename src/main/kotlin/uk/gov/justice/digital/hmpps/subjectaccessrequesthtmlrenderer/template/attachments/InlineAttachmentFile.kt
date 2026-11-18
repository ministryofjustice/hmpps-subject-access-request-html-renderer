package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments

import java.util.UUID

class InlineAttachmentFile(
  val requestId: UUID?,
  val serviceName: String,
  val contentType: String,
  val filename: String,
  val data: ByteArray,
  val filesize: Long,
) {
  val documentKey: String = "$requestId/$serviceName/inline-attachments/$filename"

  fun getImageSrc(): String = "https://sar-inline-attachments/$documentKey"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InlineAttachmentFile

    if (filesize != other.filesize) return false
    if (requestId != other.requestId) return false
    if (serviceName != other.serviceName) return false
    if (contentType != other.contentType) return false
    if (filename != other.filename) return false
    if (!data.contentEquals(other.data)) return false
    if (documentKey != other.documentKey) return false

    return true
  }

  override fun hashCode(): Int {
    var result = filesize.hashCode()
    result = 31 * result + requestId.hashCode()
    result = 31 * result + serviceName.hashCode()
    result = 31 * result + contentType.hashCode()
    result = 31 * result + filename.hashCode()
    result = 31 * result + data.contentHashCode()
    result = 31 * result + documentKey.hashCode()
    return result
  }
}
