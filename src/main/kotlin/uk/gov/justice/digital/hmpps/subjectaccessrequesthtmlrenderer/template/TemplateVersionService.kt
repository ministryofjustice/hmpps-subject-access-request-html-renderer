package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_CONFIGURATION_NOT_FOUND
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_EMPTY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_HASH_MISMATCH
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_PUBLISHED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_PUBLISH_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.renderEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class TemplateVersionService(
  val serviceConfigurationService: ServiceConfigurationService,
  val templateVersionRepository: TemplateVersionRepository,
  val dynamicServicesClient: DynamicServicesClient,
  val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(TemplateVersionService::class.java)
  }

  /**
   * Get the SAR template from the HMPPS Service and verify the file hash against the expected template version.
   */
  @Transactional
  fun getTemplate(renderRequest: RenderRequest): String {
    val serviceTemplate: String = getServiceTemplate(renderRequest)

    verifyTemplateHash(
      renderRequest = renderRequest,
      serviceTemplate = serviceTemplate,
    )

    return serviceTemplate
  }

  private fun verifyTemplateHash(renderRequest: RenderRequest, serviceTemplate: String) {
    assertServiceTemplateIsNotEmpty(renderRequest, serviceTemplate)

    val serviceConfiguration = getServiceConfiguration(renderRequest)
    val serviceTemplateHash = getSha256HashValue(serviceTemplate)

    templateVersionRepository.findLatestByServiceConfigurationId(serviceConfiguration.id)
      ?.takeIf { it.fileHash == serviceTemplateHash }
      ?.let {
        log.info(
          "service template hash matched template version: id={}, version={}, status={}, serviceName={}",
          it.id,
          it.version,
          it.status,
          serviceConfiguration.serviceName,
        )
        if (TemplateVersionStatus.PENDING == it.status) {
          publishPendingTemplateVersion(it, renderRequest, serviceTemplateHash)
        }

        it
      } ?: throw templateHashMatchFailureException(
      renderRequest = renderRequest,
      serviceTemplateHash = serviceTemplateHash,
    )
  }

  private fun getServiceTemplate(
    renderRequest: RenderRequest,
  ): String = dynamicServicesClient.getServiceTemplate(renderRequest)?.body?.takeIf { it.isNotBlank() }
    ?: throw serviceTemplateBlankException(renderRequest = renderRequest)

  private fun assertServiceTemplateIsNotEmpty(renderRequest: RenderRequest, serviceTemplate: String) {
    if (serviceTemplate.isEmpty() || serviceTemplate.isBlank()) {
      throw serviceTemplateBlankException(renderRequest = renderRequest)
    }
  }

  fun getSha256HashValue(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun publishPendingTemplateVersion(
    templateVersion: TemplateVersion,
    renderRequest: RenderRequest,
    serviceTemplateHash: String,
  ) {
    log.info(
      "publishing {} template version: id={}, version={}",
      renderRequest.serviceConfiguration.serviceName,
      templateVersion.id,
      templateVersion.version,
    )

    templateVersionRepository.findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
      id = templateVersion.id,
      version = templateVersion.version,
      status = TemplateVersionStatus.PENDING,
      fileHash = serviceTemplateHash,
    )?.let { pending ->
      pending.status = TemplateVersionStatus.PUBLISHED
      pending.publishedAt = LocalDateTime.now()
      try {
        templateVersionRepository.saveAndFlush(pending)

        telemetryClient.renderEvent(
          SERVICE_TEMPLATE_PUBLISHED,
          renderRequest.id,
          "serviceName" to renderRequest.serviceConfiguration.serviceName,
          "version" to pending.version.toString(),
        )
      } catch (ex: Exception) {
        throw publishTemplateException(renderRequest, pending, ex)
      }
    }
  }

  private fun getServiceConfiguration(
    renderRequest: RenderRequest,
  ): ServiceConfiguration = serviceConfigurationService.findByIdAndEnabledAndTemplateMigrated(
    id = renderRequest.serviceConfiguration.id,
    enabled = true,
    templateMigrated = true,
  ) ?: throw serviceConfigurationNotFoundException(renderRequest = renderRequest)

  private fun templateHashMatchFailureException(
    renderRequest: RenderRequest,
    serviceTemplateHash: String,
  ): SubjectAccessRequestException = SubjectAccessRequestException(
    message = "service template file hash does not match registered template versions",
    errorCode = ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH,
    subjectAccessRequestId = renderRequest.id!!,
    params = mapOf(
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
      "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
      "serviceTemplateHash" to serviceTemplateHash,
    ),
  ).also {
    telemetryClient.renderEvent(
      SERVICE_TEMPLATE_HASH_MISMATCH,
      renderRequest.id,
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
      "serviceConfigurationId" to renderRequest.serviceConfiguration.id.toString(),
      "serviceTemplateHash" to serviceTemplateHash,
    )
    log.error("service template file hash does not match registered template versions")
  }

  private fun serviceTemplateBlankException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestException(
    message = "service template hash error: service template was empty",
    errorCode = ErrorCode.SERVICE_TEMPLATE_EMPTY,
    subjectAccessRequestId = renderRequest.id,
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  ).also {
    telemetryClient.renderEvent(
      SERVICE_TEMPLATE_EMPTY,
      renderRequest.id,
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
    )
  }

  private fun serviceConfigurationNotFoundException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestException(
    message = "service configuration not found matching id, templateMigrated=true, and enabled=true",
    errorCode = ErrorCode.SERVICE_CONFIGURATION_NOT_FOUND,
    subjectAccessRequestId = renderRequest.id!!,
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  ).also {
    telemetryClient.renderEvent(
      SERVICE_CONFIGURATION_NOT_FOUND,
      renderRequest.id,
      "serviceConfigurationId" to renderRequest.serviceConfiguration.id.toString(),
    )
  }

  private fun publishTemplateException(
    renderRequest: RenderRequest,
    templateVersion: TemplateVersion,
    cause: Exception,
  ) = SubjectAccessRequestException(
    message = "unexpected error whilst attempting to publish template version",
    cause = cause,
    errorCode = ErrorCode.SERVICE_TEMPLATE_PUBLISH_FAILURE,
    subjectAccessRequestId = renderRequest.id,
    params = mapOf(
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
      "version" to templateVersion.version,
      "templateVersionId" to templateVersion.id,
    ),
  ).also {
    telemetryClient.renderEvent(
      SERVICE_TEMPLATE_PUBLISH_ERROR,
      renderRequest.id,
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
      "version" to templateVersion.version.toString(),
      "templateVersionId" to templateVersion.id.toString(),
    )
  }
}
