package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import java.util.UUID

data class RenderResponse(val cacheKey: String) {
  constructor(subjectAccessRequestId: UUID, serviceName: String) : this(
    cacheKey = "${subjectAccessRequestId}_$serviceName",
  )
}
