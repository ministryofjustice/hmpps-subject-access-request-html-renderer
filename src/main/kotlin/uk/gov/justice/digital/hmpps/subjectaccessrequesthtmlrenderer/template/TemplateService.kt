package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.slf4j.LoggerFactory
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
) {

  private companion object {
    private val log = LoggerFactory.getLogger(TemplateService::class.java)
  }

  fun getStyleTemplate(): String = getTemplateResourceOrNull("$templatesDirectory/main_stylesheet.mustache") ?: ""

  fun getRenderParameters(
    renderRequest: RenderRequest,
    data: Any?,
  ): RenderParameters = data?.let { getDataHeldParameters(renderRequest, it) } ?: getNoDataHeldParameters(renderRequest)

  private fun getDataHeldParameters(
    renderRequest: RenderRequest,
    data: Any?,
  ): RenderParameters = renderRequest.takeIf { it.serviceConfiguration.templateMigrated }?.let {
    log.info("creating renderParameters for migrated service template: {}", it.serviceConfiguration.serviceName)

    templateVersionService.getTemplate(renderRequest).toRenderParameters(data)
  } ?: run {
    log.info("creating renderParameters for local service template: {}", renderRequest.serviceConfiguration.serviceName)

    RenderParameters(
      templateVersion = "v1-migrated-false",
      data = data,
      template = getTemplateResource(
        renderRequest = renderRequest,
        resourcePath = "$templatesDirectory/template_${renderRequest.serviceConfiguration.serviceName}.mustache",
      ).readText(),
    )
  }

  private fun getNoDataHeldParameters(renderRequest: RenderRequest) = RenderParameters(
    templateVersion = "v1-no-data",
    data = renderRequest.serviceNameMap(),
    template = getTemplateResource(
      renderRequest = renderRequest,
      resourcePath = "$templatesDirectory/template_no_data.mustache",
    ).readText(),
  )

  private fun getTemplateResource(renderRequest: RenderRequest, resourcePath: String) = this::class.java
    .getResource(resourcePath) ?: throw templateResourceNotFoundException(renderRequest, resourcePath)

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

  private fun TemplateDetails.toRenderParameters(data: Any?): RenderParameters = RenderParameters(
    templateVersion = this.version,
    template = this.body,
    data = data,
  )

  private fun RenderRequest.serviceNameMap() = mapOf("serviceLabel" to this.serviceConfiguration.label)
}
