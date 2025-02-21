package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SubjectAccessRequestHtmlRenderer

fun main(args: Array<String>) {
  runApplication<SubjectAccessRequestHtmlRenderer>(*args)
}
