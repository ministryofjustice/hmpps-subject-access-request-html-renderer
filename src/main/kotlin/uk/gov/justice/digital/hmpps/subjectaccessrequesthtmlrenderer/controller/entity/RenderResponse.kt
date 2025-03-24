package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import java.util.UUID

data class RenderResponse(val documentKey: String) {
  constructor(subjectAccessRequestId: UUID, serviceName: String) : this(
    documentKey = "$subjectAccessRequestId/$serviceName.html",
  )
}
