package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.github.mustachejava.DefaultMustacheFactory
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.charset.StandardCharsets

@Service
class TemplateRenderingService(
  private val templateRenderService: TemplateRenderService,
  private val templateService: TemplateService,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(TemplateRenderingService::class.java)
  }

  fun renderServiceDataHtml(renderRequest: RenderRequest, data: Any?): RenderedHtml {
    telemetryClient.renderEvent(RENDER_TEMPLATE_STARTED, renderRequest)
    log.info(
      "starting html render for id={}, service={}",
      renderRequest.serviceNameMap(),
      renderRequest.serviceConfiguration.serviceName,
    )

    val renderParameters = templateService.getRenderParameters(renderRequest, data)
    val renderedServiceTemplate = templateRenderService.renderServiceTemplate(renderParameters)
    val outputStream = renderStyleTemplate(renderedServiceTemplate).also {
      telemetryClient.renderEvent(RENDER_TEMPLATE_COMPLETED, renderRequest)
      log.info(
        "completed html render for id={}, service={}",
        renderRequest.serviceNameMap(),
        renderRequest.serviceConfiguration.serviceName,
      )
    }

    return RenderedHtml(
      data = outputStream,
      templateVersion = renderParameters.templateVersion,
    )
  }

  private fun renderStyleTemplate(renderedServiceTemplate: String): ByteArrayOutputStream {
    val defaultMustacheFactory = DefaultMustacheFactory()
    val styleTemplate = templateService.getStyleTemplate()
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

  fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to this.serviceConfiguration.label)
}

data class RenderedHtml(val data: ByteArrayOutputStream?, val templateVersion: String)
