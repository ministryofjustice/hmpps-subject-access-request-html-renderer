package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.FileBasedTemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TempFile
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.RENDER_TEMPLATE_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest

@Service
class TemplateRenderingService(
  private val fileBasedTemplateRenderService: FileBasedTemplateRenderService,
  private val templateService: TemplateService,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(TemplateRenderingService::class.java)
  }

  fun renderServiceDataHtml(
    renderRequest: RenderRequest,
    data: Any?,
    renderOutputFile: TempFile,
  ): TemplateVersionInfo {
    telemetryClient.renderEvent(RENDER_TEMPLATE_STARTED, renderRequest)
    log.info(
      "starting html render for id={}, service={}",
      renderRequest.serviceNameMap(),
      renderRequest.serviceConfiguration.serviceName,
    )

    val renderParameters = templateService.getRenderParameters(renderRequest, data)

    try {
      fileBasedTemplateRenderService.renderServiceTemplate(
        params = renderParameters,
        renderRequestInfo = renderRequest.toRenderRequestInfo(),
        outputTarget = renderOutputFile,
      )

      telemetryClient.renderEvent(RENDER_TEMPLATE_COMPLETED, renderRequest)
      log.info(
        "completed html render for id={}, service={}",
        renderRequest.serviceNameMap(),
        renderRequest.serviceConfiguration.serviceName,
      )

      return TemplateVersionInfo(renderParameters.templateVersion)
    } catch (e: Exception) {
      log.error("failed to render template for subject access request {}", renderRequest.id, e)
      telemetryClient.renderEvent(RENDER_TEMPLATE_ERROR, renderRequest, e)
      throw e
    }
  }

  fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to this.serviceConfiguration.label)
}

data class TemplateVersionInfo(val version: String) {
  fun formattedVersion(): String = "v$version"
}
