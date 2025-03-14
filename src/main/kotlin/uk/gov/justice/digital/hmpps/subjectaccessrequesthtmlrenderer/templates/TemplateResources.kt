package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException

@Service
class TemplateResources(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
) {

  fun getStyleTemplate(): String = getTemplateResourceOrNull("$templatesDirectory/main_stylesheet.mustache") ?: ""

  fun getServiceTemplate(renderRequest: RenderRequest): String {
    val serviceName = validateServiceName(renderRequest)

    return getTemplateResource(
      renderRequest = renderRequest,
      resourcePath = "$templatesDirectory/template_$serviceName.mustache",
    ).readText()
  }

  private fun validateServiceName(renderRequest: RenderRequest): String {
    if (StringUtils.isEmpty(renderRequest.serviceName)) {
      throw missingServiceNameException(renderRequest)
    }
    return renderRequest.serviceName!!
  }

  private fun getTemplateResourceOrNull(path: String) = this::class.java.getResource(path)?.readText()

  private fun getTemplateResource(renderRequest: RenderRequest, resourcePath: String) = this::class.java
    .getResource(resourcePath) ?: throw templateResourceNotFoundException(renderRequest, resourcePath)

  private fun templateResourceNotFoundException(renderRequest: RenderRequest, resourcePath: String) = SubjectAccessRequestTemplatingException(
    subjectAccessRequestId = renderRequest.id,
    message = "template resource not found",
    params = mapOf(
      "resource" to resourcePath,
    ),
  )

  private fun missingServiceNameException(renderRequest: RenderRequest) = SubjectAccessRequestTemplatingException(
    subjectAccessRequestId = renderRequest.id,
    message = "unable to load service template: service name was null or empty",
  )
}
