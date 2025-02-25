package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import java.util.UUID

open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  val subjectAccessRequestId: UUID? = null,
  val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  constructor(message: String) : this(message, null, null, null)

  /**
   * Return the exception message with additional details.
   */
  override val message: String?
    get() = buildString {
      append(super.message)
      cause?.message?.let { append(", cause=$it") }
      append(", id=$subjectAccessRequestId")
      params?.toFormattedString()?.let { append(", $it") }
    }

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    "${entry.key}=${entry.value}"
  }
}
