package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import com.github.jknack.handlebars.Handlebars
import com.github.mustachejava.DefaultMustacheFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateResources
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Service
class TemplateService(
  private val templateHelpers: TemplateHelpers,
  private val templateResources: TemplateResources,
) {

  fun renderServiceDataHtml(renderRequest: RenderRequest, data: Any?): ByteArrayOutputStream? {
    val renderedServiceTemplate = renderServiceTemplate(renderRequest, data) ?: return null
    return renderStyleTemplate(renderedServiceTemplate)
  }

  private fun renderServiceTemplate(
    renderRequest: RenderRequest,
    serviceData: Any?,
  ): String? {
    val handlebars = Handlebars()
    handlebars.registerHelpers(templateHelpers)
    val serviceTemplate = templateResources.getServiceTemplate(renderRequest) ?: return null

    val compiledServiceTemplate = handlebars.compileInline(serviceTemplate)
    val renderedServiceTemplate = compiledServiceTemplate.apply(serviceData)
    return renderedServiceTemplate.toString()
  }

  private fun renderStyleTemplate(renderedServiceTemplate: String): ByteArrayOutputStream {
    val defaultMustacheFactory = DefaultMustacheFactory()
    val styleTemplate = templateResources.getStyleTemplate()
    val compiledStyleTemplate = defaultMustacheFactory.compile(StringReader(styleTemplate), "styleTemplate")

    val out = ByteArrayOutputStream()
    BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { writer ->
      compiledStyleTemplate.execute(
        writer,
        mapOf("serviceTemplate" to renderedServiceTemplate),
      ).flush()
    }
    return out
  }
}
