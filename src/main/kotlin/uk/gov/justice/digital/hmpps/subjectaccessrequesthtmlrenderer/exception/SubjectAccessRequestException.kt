package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode.WEB_CLIENT_NON_RETRYABLE_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode.WEB_CLIENT_RETRY_EXHAUSTED
import java.util.UUID

/**
 * Top level subject access request exception type
 */
open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  val errorCode: ErrorCode,
  val subjectAccessRequestId: UUID? = null,
  val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  /**
   * Return the exception message with additional details.
   */
  override val message: String?
    get() = buildString {
      append(super.message)
      cause?.message?.let { append(", cause=$it") }
      append(", errorCode=${errorCode.codeString()}")
      append(", id=$subjectAccessRequestId")
      params?.toFormattedString()?.let { append(", $it") }
    }

  fun messageOrDefault(): String = message ?: this::class.java.simpleName

  fun paramsToPairsArray(): Array<Pair<String, String>> = this.params
    ?.map { Pair(it.key, it.value.toString()) }
    ?.toTypedArray()
    ?: emptyArray()

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    "${entry.key}=${entry.value}"
  }
}

/**
 * Exception type for fatal/non retryable errors.
 */
class FatalSubjectAccessRequestException(
  message: String,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  message = FATAL_ERROR_MESSAGE_PREFIX.format(message),
  errorCode = WEB_CLIENT_NON_RETRYABLE_ERROR,
  cause = cause,
  subjectAccessRequestId = subjectAccessRequestId,
  params = params,
) {

  constructor(
    message: String,
    subjectAccessRequestId: UUID? = null,
    params: Map<String, *>? = null,
  ) : this(message, null, subjectAccessRequestId, params)

  companion object {
    private const val FATAL_ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed with non-retryable error: %s"
  }
}

/**
 * Exception type for retry exhausted errors
 */
class SubjectAccessRequestRetryExhaustedException(
  retryAttempts: Long,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  message = RETRY_EXHAUSTED_ERROR_MESSAGE_PREFIX.format(retryAttempts),
  cause = cause,
  errorCode = WEB_CLIENT_RETRY_EXHAUSTED,
  subjectAccessRequestId = subjectAccessRequestId,
  params = params,
) {

  companion object {
    private const val RETRY_EXHAUSTED_ERROR_MESSAGE_PREFIX = "request failed and max retry attempts (%s) exhausted"
  }
}
