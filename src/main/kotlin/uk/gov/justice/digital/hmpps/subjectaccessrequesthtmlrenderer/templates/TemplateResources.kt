package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.TemplateResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceTemplateMetadata
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.ServiceConfigurationRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.ServiceTemplateMetadataRepository

@Service
class TemplateResources(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
  private val dynamicServicesClient: DynamicServicesClient? = null,
  private val serviceTemplateMetadataRepository: ServiceTemplateMetadataRepository? = null,
  private val serviceConfigurationRepository: ServiceConfigurationRepository? = null,
) {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(TemplateResources::class.java)
  }

  fun getStyleTemplate(): String = getTemplateResourceOrNull("$templatesDirectory/main_stylesheet.mustache") ?: ""

  @Transactional
  fun getServiceTemplate(renderRequest: RenderRequest): String {
    validateServiceName(renderRequest)
    return getTemplateVersion(renderRequest)
  }

  fun getNoDataTemplate(renderRequest: RenderRequest): String = getTemplateResource(
    renderRequest = renderRequest,
    resourcePath = "$templatesDirectory/template_no_data.mustache",
  ).readText()

  private fun validateServiceName(renderRequest: RenderRequest): String {
    if (StringUtils.isEmpty(renderRequest.serviceName)) {
      throw missingServiceNameException(renderRequest)
    }
    return renderRequest.serviceName!!
  }

  private fun getTemplateResourceOrNull(path: String) = this::class.java.getResource(path)?.readText()

  private fun getTemplateResource(renderRequest: RenderRequest, resourcePath: String) = this::class.java
    .getResource(resourcePath) ?: throw templateResourceNotFoundException(renderRequest, resourcePath)

  private fun templateResourceNotFoundException(renderRequest: RenderRequest, resourcePath: String) =
    SubjectAccessRequestTemplatingException(
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

  private fun getTemplateVersion(
    renderRequest: RenderRequest,
  ): String = dynamicServicesClient?.getServiceTemplate(renderRequest)?.let { response ->
    response.validate()

    serviceTemplateMetadataRepository!!.findLatestByServiceName(renderRequest.serviceName!!)?.let { latestKnown ->
      checkForTemplateVersionRegression(latestKnown, response)
      updateLatestKnown(latestKnown, response)
    } ?: saveInitialTemplateVersion(renderRequest, response)

    response.templateBody
  } ?: getTemplateResource(
    renderRequest = renderRequest,
    resourcePath = "$templatesDirectory/template_${renderRequest.serviceName}.mustache",
  ).readText()


  fun TemplateResponse.validate() {
    if (this.version == null) {
      throw RuntimeException("Invalid template version for $serviceName")
    }
    if (this.templateBody.isNullOrEmpty()) {
      throw RuntimeException("Invalid template body for $serviceName")
    }
  }

  fun saveInitialTemplateVersion(renderRequest: RenderRequest, templateResponse: TemplateResponse) {
    log.info("saving initial service template metadata for {}", renderRequest.serviceName)

    serviceConfigurationRepository!!.findByServiceName(renderRequest.serviceName!!)?.let {
      serviceTemplateMetadataRepository!!.save(
        ServiceTemplateMetadata(
          version = templateResponse.version!!,
          serviceConfiguration = it,
        ),
      )
    } ?: throw RuntimeException("Service configuration for ${renderRequest.serviceName} not found")
  }

  fun updateLatestKnown(latestKnown: ServiceTemplateMetadata, templateResponse: TemplateResponse) {
    if (templateResponse.version!! > latestKnown.version) {
      log.info("updating ${templateResponse.serviceName} latest service template version from ${latestKnown.version} to ${templateResponse.version}")

      serviceTemplateMetadataRepository!!.save(
        ServiceTemplateMetadata(
          version = templateResponse.version!!,
          serviceConfiguration = latestKnown.serviceConfiguration,
        ),
      )
    }
  }

  fun checkForTemplateVersionRegression(
    latestKnown: ServiceTemplateMetadata,
    serviceTemplateResponse: TemplateResponse,
  ) {
    if (serviceTemplateResponse.version!! < latestKnown.version) {
      throw RuntimeException(
        "${serviceTemplateResponse.serviceName} template version regression, latest known: ${latestKnown.version}, api version: ${serviceTemplateResponse.version}",
      )
    }
  }
}
