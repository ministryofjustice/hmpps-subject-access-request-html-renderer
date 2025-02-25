package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.io.ByteArrayOutputStream

@Service
class TemplateService {

  fun renderServiceDataHtml(renderRequest: RenderRequest, data: Map<*, *>?): ByteArrayOutputStream {
    // TODO complete implementation
    return ByteArrayOutputStream().also { stream ->
      stream.writeBytes("Hello World!!".toByteArray())
    }
  }
}
