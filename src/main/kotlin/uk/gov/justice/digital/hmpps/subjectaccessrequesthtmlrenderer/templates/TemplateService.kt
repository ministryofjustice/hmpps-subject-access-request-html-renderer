package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import com.github.jknack.handlebars.Handlebars
import com.github.mustachejava.DefaultMustacheFactory
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Service
class TemplateService(
  private val templateHelpers: TemplateHelpers,
  private val templateResources: TemplateResources,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(TemplateService::class.java)
  }

  fun renderServiceDataHtml(renderRequest: RenderRequest, data: Any?): ByteArrayOutputStream? {
    telemetryClient.renderEvent(RENDER_TEMPLATE_STARTED, renderRequest)
    log.info("starting html render for id={}, service={}", renderRequest.serviceNameMap(), renderRequest.serviceName)

    val renderParameters = getRenderParameters(renderRequest, data)
    val renderedServiceTemplate = renderServiceTemplate(renderParameters)
    return renderStyleTemplate(renderedServiceTemplate).also {
      telemetryClient.renderEvent(RENDER_TEMPLATE_COMPLETED, renderRequest)
      log.info("completed html render for id={}, service={}", renderRequest.serviceNameMap(), renderRequest.serviceName)
    }
  }

  private fun getRenderParameters(
    renderRequest: RenderRequest,
    data: Any?,
  ): RenderParameters = if (data != null) {
    RenderParameters(templateResources.getServiceTemplate(renderRequest), data)
  } else {
    RenderParameters(templateResources.getNoDataTemplate(renderRequest), renderRequest.serviceNameMap())
  }

  private fun renderServiceTemplate(params: RenderParameters): String {
    val handlebars = Handlebars()
    handlebars.registerHelpers(templateHelpers)
    val compiledServiceTemplate = handlebars.compileInline(params.template)
    val renderedServiceTemplate = compiledServiceTemplate.apply(params.data)
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

  data class RenderParameters(val template: String, val data: Any?)

  fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to (this.serviceLabel ?: "UNKNOWN"))
}
