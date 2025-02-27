package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException

@Service
class TemplateResources(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
  @Value("\${template-resources.mandatory}") private val mandatoryServiceTemplates: List<String> = listOf(
    "G1",
    "G2",
    "G3",
  ),
) {

  companion object {
    private val LOG = LoggerFactory.getLogger(TemplateResources::class.java)
  }

  fun getServiceTemplate(renderRequest: RenderRequest): String? {
    val template = getResource("$templatesDirectory/template_${renderRequest.serviceName!!}.mustache")

    if (serviceTemplateIsMandatory(renderRequest.serviceName) && template == null) {
      throw SubjectAccessRequestTemplatingException(
        subjectAccessRequestId = renderRequest.id,
        message = "mandatory service template does not exist",
        params = mapOf(
          "service" to renderRequest.serviceName,
          "requiredTemplate" to "$templatesDirectory/template_$renderRequest.serviceName.mustache",
        ),
      )
    }
    return template
  }

  fun getStyleTemplate(): String = getResource("$templatesDirectory/main_stylesheet.mustache") ?: ""

  private fun serviceTemplateIsMandatory(serviceName: String) = mandatoryServiceTemplates.contains(serviceName).also {
    LOG.info("is mandatory service template? $it, config: ${mandatoryServiceTemplates.joinToString(",")}")
  }

  private fun getResource(path: String) = this::class.java.getResource(path)?.readText()
}
