package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import java.util.UUID

data class RenderResponse(val documentKey: String, val templateVersion: String) {
  constructor(subjectAccessRequestId: UUID, serviceName: String, templateVersion: String) : this(
    documentKey = "$subjectAccessRequestId/$serviceName.html",
    templateVersion = templateVersion,
  )
}
