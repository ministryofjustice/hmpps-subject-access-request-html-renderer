package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.io.ByteArrayOutputStream

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
    val outputStream =
      templateRenderService.renderServiceTemplate(renderParameters, renderRequest.toRenderRequestInfo()).also {
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

  fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to this.serviceConfiguration.label)
}

data class RenderedHtml(val data: ByteArrayOutputStream?, val templateVersion: String)
