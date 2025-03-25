package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

class SubjectAccessRequestResourceNotFoundException(resource: String) :
  RuntimeException(
    "Subject access request resource $resource not found",
  )
