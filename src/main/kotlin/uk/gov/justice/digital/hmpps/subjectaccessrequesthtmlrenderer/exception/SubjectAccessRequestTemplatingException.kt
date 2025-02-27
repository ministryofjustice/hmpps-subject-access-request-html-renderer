package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

import java.util.UUID

class SubjectAccessRequestTemplatingException(
  subjectAccessRequestId: UUID? = null,
  message: String,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  subjectAccessRequestId = subjectAccessRequestId,
  message = message,
  params = params,
) {
  constructor(message: String, params: Map<String, *>? = null) : this(
    subjectAccessRequestId = null,
    message = message,
    params = params,
  )
}
