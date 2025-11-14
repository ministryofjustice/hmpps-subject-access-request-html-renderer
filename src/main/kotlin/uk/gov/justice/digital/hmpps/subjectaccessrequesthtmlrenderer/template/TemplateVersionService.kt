package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestServiceTemplateException
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

    findTemplateVersionForServiceConfigIdAndFileHash(serviceConfiguration, serviceTemplateHash)
      ?.let { matchedTemplateVersion ->
        matchedTemplateVersion.takeIf { it.status == TemplateVersionStatus.PENDING }?.let {
          publishPendingTemplateVersion(it, renderRequest, serviceTemplateHash)
        }
        matchedTemplateVersion
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

  private fun findTemplateVersionForServiceConfigIdAndFileHash(
    serviceConfiguration: ServiceConfiguration,
    serviceTemplateHash: String,
  ): TemplateVersion? = templateVersionRepository.findLatestByServiceConfigurationIdAndFileHash(
    serviceConfigurationId = serviceConfiguration.id,
    fileHash = serviceTemplateHash,
  ).also {
    it?.let {
      log.info(
        "service template hash matched template version: id={}, version={}, status={}, serviceName={}",
        it.id,
        it.version,
        it.status,
        serviceConfiguration.serviceName,
      )
    }
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
  ): SubjectAccessRequestServiceTemplateException = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id!!,
    message = "service template file hash does not match registered template versions",
    params = mapOf(
      "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
      "serviceTemplateHash" to serviceTemplateHash,
    ),
  ).also { log.error("service template file hash does not match registered template versions") }

  private fun serviceTemplateBlankException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id,
    message = "service template hash error: service template was empty",
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  )

  private fun serviceConfigurationNotFoundException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id!!,
    message = "service configuration not found matching id, templateMigrated=true, and enabled=true",
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  )

  private fun publishTemplateException(
    renderRequest: RenderRequest,
    templateVersion: TemplateVersion,
    cause: Exception,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id,
    "unexpected error whilst attempting to publish template version",
    cause = cause,
    params = mapOf(
      "serviceName" to renderRequest.serviceConfiguration.serviceName,
      "version" to templateVersion.version,
      "templateVersionId" to templateVersion.id,
    ),
  )
}
