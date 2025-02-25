package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import java.util.UUID

private const val FATAL_ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed with non-retryable error: %s"

class FatalSubjectAccessRequestException(
  message: String,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  FATAL_ERROR_MESSAGE_PREFIX.format(message),
  cause,
  subjectAccessRequestId,
  params,
) {

  constructor(
    message: String,
    subjectAccessRequestId: UUID? = null,
    params: Map<String, *>? = null,
  ) : this(message, null, subjectAccessRequestId, params)
}
