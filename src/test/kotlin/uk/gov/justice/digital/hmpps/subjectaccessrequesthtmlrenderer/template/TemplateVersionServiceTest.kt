package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestServiceTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import java.util.UUID

class TemplateVersionServiceTest : TemplateVersionServiceTestFixture() {

  @Nested
  inner class VerifyTemplateHashError {

    @Test
    fun `should throw exception when template body is empty`() {
      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, "")
      }

      assertThat(actual).isNotNull()
      assertThat(actual).hasMessageContaining("verify template hash error: service template was empty")

      verifyNoInteractions(serviceConfigurationService, templateVersionRepository)
    }

    @Test
    fun `should throw exception when service configuration does not exist`() {
      val idCaptor = argumentCaptor<UUID>()
      val enabledCaptor = argumentCaptor<Boolean>()
      val templateMigratedCaptor = argumentCaptor<Boolean>()

      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = null,
      )

      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, publishedTemplateBody)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)
      assertThat(actual.message).startsWith("service configuration not found matching id, templateMigrated=true, and enabled=true")
      assertThat(actual.params).containsEntry("serviceConfigurationId", serviceConfig.id)

      verify(serviceConfigurationService, times(1)).findByIdAndEnabledAndTemplateMigrated(
        id = idCaptor.capture(),
        enabled = enabledCaptor.capture(),
        templateMigrated = templateMigratedCaptor.capture(),
      )

      assertThat(idCaptor.firstValue).isEqualTo(serviceConfig.id)
      assertThat(enabledCaptor.firstValue).isTrue()
      assertThat(templateMigratedCaptor.firstValue).isTrue()

      verifyNoInteractions(templateVersionRepository)
    }

    @Test
    fun `should throw exception if error when updating a template status from PENDING to PUBLISHED`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndFileHash(
        fileHash = pendingTemplateHash,
        returnValue = pendingTemplateVersion,
      )
      mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = pendingTemplateVersion.version,
        fileHash = pendingTemplateHash,
        returnValue = pendingTemplateVersion,
      )

      val saveCaptor = argumentCaptor<TemplateVersion>()

      mockSaveAllThrowsException()

      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, pendingTemplateBody)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)
      assertThat(actual.cause).isNotNull()
      assertThat(actual.message).startsWith("unexpected error whilst attempting to publish template version")
      assertThat(actual.params).containsAllEntriesOf(
        mapOf(
          "serviceName" to serviceConfig.serviceName,
          "version" to pendingTemplateVersion.version,
          "templateVersionId" to pendingTemplateVersion.id,
        ),
      )

      verify(templateVersionRepository, times(1))
        .findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
          id = pendingTemplateVersion.id,
          version = 2,
          status = TemplateVersionStatus.PENDING,
          fileHash = pendingTemplateHash,
        )

      verify(templateVersionRepository, times(1)).saveAndFlush(saveCaptor.capture())

      assertThat(saveCaptor.firstValue.id).isEqualTo(pendingTemplateVersion.id)
      assertThat(saveCaptor.firstValue.version).isEqualTo(2)
      assertThat(saveCaptor.firstValue.status).isEqualTo(TemplateVersionStatus.PUBLISHED)
      assertThat(saveCaptor.firstValue.fileHash).isEqualTo(pendingTemplateHash)
    }

    @Test
    fun `should throw exception when service template hash does not match any PUBLISHED or PENDING template versions`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndFileHash(
        fileHash = publishedTemplateHash,
        returnValue = null,
      )

      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, publishedTemplateBody)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)
      assertThat(actual).hasMessageContaining("service template file hash does not match registered template versions")
      assertThat(actual.params).containsAllEntriesOf(
        mapOf(
          "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
          "serviceTemplateHash" to publishedTemplateHash,
        ),
      )

      verify(serviceConfigurationService, times(1)).findByIdAndEnabledAndTemplateMigrated(
        id = serviceConfig.id,
        enabled = true,
        templateMigrated = true,
      )
      verify(templateVersionRepository, times(1)).findLatestByServiceConfigurationIdAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        fileHash = publishedTemplateHash,
      )

      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }
  }

  @Nested
  inner class VerifyTemplateHashSuccess {

    @Test
    fun `should succeed when service template hash matches PUBLISHED template version hash`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndFileHash(
        fileHash = publishedTemplateHash,
        returnValue = publishedTemplateVersion,
      )

      templateVersionService.verifyTemplateHash(renderRequest, publishedTemplateBody)

      verify(serviceConfigurationService, times(1)).findByIdAndEnabledAndTemplateMigrated(
        id = serviceConfig.id,
        enabled = true,
        templateMigrated = true,
      )
      verify(templateVersionRepository, times(1)).findLatestByServiceConfigurationIdAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        fileHash = publishedTemplateHash,
      )
      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should succeed and update template version to status PUBLISHED when service template hash matches template version hash with status PENDING`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()

      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndFileHash(
        fileHash = pendingTemplateHash,
        returnValue = pendingTemplateVersion,
      )
      mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = 2,
        fileHash = pendingTemplateHash,
        status = TemplateVersionStatus.PENDING,
        returnValue = pendingTemplateVersion,
      )

      templateVersionService.verifyTemplateHash(renderRequest, pendingTemplateBody)

      verify(serviceConfigurationService, times(1)).findByIdAndEnabledAndTemplateMigrated(
        id = serviceConfig.id,
        enabled = true,
        templateMigrated = true,
      )
      verify(templateVersionRepository, times(1)).findLatestByServiceConfigurationIdAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        fileHash = pendingTemplateHash,
      )
      verify(templateVersionRepository, times(1)).findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = pendingTemplateHash,
      )
      verify(templateVersionRepository, times(1)).saveAndFlush(saveCaptor.capture())

      assertThat(saveCaptor.firstValue).isNotNull
      assertThat(saveCaptor.firstValue.id).isEqualTo(pendingTemplateVersion.id)
      assertThat(saveCaptor.firstValue.version).isEqualTo(2)
      assertThat(saveCaptor.firstValue.status).isEqualTo(TemplateVersionStatus.PUBLISHED)
      assertThat(saveCaptor.firstValue.publishedAt).isNotNull
      assertThat(saveCaptor.firstValue.fileHash).isEqualTo(pendingTemplateHash)

      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }
  }

  @Nested
  inner class GetSha256HashValue {

    @Test
    fun `should return expected SHA-256 value`() {
      assertThat(templateVersionService.getSha256HashValue(publishedTemplateBody))
        .isEqualTo(publishedTemplateHash)
    }
  }
}
