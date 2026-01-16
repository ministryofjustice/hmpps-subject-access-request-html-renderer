package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.RenderParameters
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode.TEMPLATE_RESOURCE_NOT_FOUND
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest

@Service
class TemplateService(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
  val templateVersionService: TemplateVersionService,
  val templateVersionHealthService: TemplateVersionHealthService,
) {

  fun getStyleTemplate(): String = getTemplateResourceOrNull("$templatesDirectory/main_stylesheet.mustache") ?: ""

  fun getRenderParameters(
    renderRequest: RenderRequest,
    data: Any?,
  ): RenderParameters = createRenderParameters(data, renderRequest, getTemplateDetails(renderRequest))

  private fun getTemplateDetails(
    renderRequest: RenderRequest,
  ): TemplateDetails = renderRequest.takeIf { it.serviceConfiguration.templateMigrated }?.let {
    templateVersionHealthService.ensureTemplateVersionHealthStatusExists(it.serviceConfiguration)

    templateVersionService.getTemplate(renderRequest)
  } ?: TemplateDetails(
    version = "legacy",
    body = getTemplate(
      renderRequest = renderRequest,
      resourcePath = "$templatesDirectory/template_${renderRequest.serviceConfiguration.serviceName}.mustache",
    ),
  )

  private fun createRenderParameters(
    data: Any?,
    renderRequest: RenderRequest,
    templateDetails: TemplateDetails,
  ): RenderParameters = data?.let {
    RenderParameters(
      templateVersion = templateDetails.version,
      template = templateDetails.body,
      data = data,
    )
  } ?: RenderParameters(
    templateVersion = templateDetails.version,
    template = getNoDataHeldTemplate(renderRequest),
    data = renderRequest.serviceNameMap(),
  )

  private fun getNoDataHeldTemplate(renderRequest: RenderRequest): String = getTemplate(
    renderRequest = renderRequest,
    resourcePath = "$templatesDirectory/template_no_data.mustache",
  )

  private fun getTemplate(renderRequest: RenderRequest, resourcePath: String): String = this::class.java
    .getResource(resourcePath)?.readText() ?: throw templateResourceNotFoundException(renderRequest, resourcePath)

  private fun getTemplateResourceOrNull(path: String) = this::class.java.getResource(path)?.readText()

  private fun templateResourceNotFoundException(
    renderRequest: RenderRequest,
    resourcePath: String,
  ) = SubjectAccessRequestException(
    message = "template resource not found",
    errorCode = TEMPLATE_RESOURCE_NOT_FOUND,
    subjectAccessRequestId = renderRequest.id,
    params = mapOf(
      "resource" to resourcePath,
    ),
  )

  private fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to this.serviceConfiguration.label)
}
