package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import java.util.UUID

private const val ERROR_MESSAGE_PREFIX = "request failed and max retry attempts (%s) exhausted"

class SubjectAccessRequestRetryExhaustedException(
  retryAttempts: Long,
  cause: Throwable?,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  ERROR_MESSAGE_PREFIX.format(retryAttempts),
  cause,
  subjectAccessRequestId,
  params,
) {

  constructor(
    retryAttempts: Long,
    subjectAccessRequestId: UUID? = null,
    params: Map<String, *>? = null,
  ) : this(retryAttempts, null, subjectAccessRequestId, params)
}
