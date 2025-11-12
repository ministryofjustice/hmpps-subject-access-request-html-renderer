package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestServiceTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class TemplateVersionService(
  val serviceConfigurationService: ServiceConfigurationService,
  val templateVersionRepository: TemplateVersionRepository,
) {

  companion object {
    private val log = LoggerFactory.getLogger(TemplateVersionService::class.java)
  }

  @Transactional
  fun verifyTemplateHash(renderRequest: RenderRequest, serviceTemplate: String) {
    assertServiceTemplateIsNotEmpty(renderRequest, serviceTemplate)

    val serviceConfiguration = getServiceConfiguration(renderRequest)
    val serviceTemplateHash = getSha256HashValue(serviceTemplate)

    getTemplateVersionMatching(serviceConfiguration, serviceTemplateHash, TemplateVersionStatus.PUBLISHED)
      ?: getTemplateVersionMatching(serviceConfiguration, serviceTemplateHash, TemplateVersionStatus.PENDING)
        ?.let { publishPendingTemplateVersion(renderRequest, serviceConfiguration, it) }
      ?: throw templateHashMatchFailureException(
        renderRequest = renderRequest,
        serviceTemplateHash = serviceTemplateHash,
      )
  }

  private fun assertServiceTemplateIsNotEmpty(renderRequest: RenderRequest, serviceTemplate: String) {
    if (serviceTemplate.isEmpty() || serviceTemplate.isBlank()) {
      throw serviceTemplateBlankException(renderRequest = renderRequest)
    }
  }

  fun getSha256HashValue(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }


  private fun getTemplateVersionMatching(
    serviceConfiguration: ServiceConfiguration,
    serviceTemplateHash: String,
    status: TemplateVersionStatus,
  ): TemplateVersion? = templateVersionRepository.findLatestByServiceConfigurationIdAndStatusAndFileHash(
    serviceConfigurationId = serviceConfiguration.id,
    status = status,
    fileHash = serviceTemplateHash,
  ).also {
    it?.let {
      log.info(
        "service template hash matched template version: id={}, version={}, status={}, serviceName={}",
        it.id,
        it.version,
        status,
        serviceConfiguration.serviceName,
      )
    }
  }

  private fun publishPendingTemplateVersion(
    renderRequest: RenderRequest,
    serviceConfig: ServiceConfiguration,
    pendingTemplateVersion: TemplateVersion,
  ) {
    log.info("updating template version status from PENDING to PUBLISHED, service: {}", serviceConfig.serviceName)

    val updated = templateVersionRepository.updateStatusAndPublishedAtByIdAndVersion(
      id = pendingTemplateVersion.id,
      version = pendingTemplateVersion.version,
      newStatus = TemplateVersionStatus.PUBLISHED,
      oldStatus = TemplateVersionStatus.PENDING,
      publishedAt = LocalDateTime.now(),
    )
    if (updated != 1) {
      throw incorrectTemplateUpdateCountException(
        renderRequest = renderRequest,
        templateVersionId = pendingTemplateVersion.id,
        updatedCount = updated,
      )
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

  private fun incorrectTemplateUpdateCountException(
    renderRequest: RenderRequest,
    templateVersionId: UUID,
    updatedCount: Int,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id!!,
    message = "update template version status to PUBLISHED did not return expected result",
    params = mapOf(
      "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
      "templateVersionId" to templateVersionId,
      "updated" to updatedCount,
      "expectedUpdates" to 1,
    ),
  )

  private fun serviceTemplateBlankException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id,
    message = "verify template hash error: service template was empty",
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  )

  private fun serviceConfigurationNotFoundException(
    renderRequest: RenderRequest,
  ) = SubjectAccessRequestServiceTemplateException(
    subjectAccessRequestId = renderRequest.id!!,
    message = "service configuration not found matching id, templateMigrated=true, and enabled=true",
    params = mapOf("serviceConfigurationId" to renderRequest.serviceConfiguration.id),
  )
}
