package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestServiceTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TemplateVersionServiceTest {

  private val serviceConfigurationService: ServiceConfigurationService = mock()
  private val templateVersionRepository: TemplateVersionRepository = mock()

  private val serviceTemplateBodyV1 = "<h1>HMPPS Test Service</h1>"
  private val expectedServiceTemplateV1Hash = "2340d53311fcf9aeaadeb6c90020d5ec77db229b342b0e0d088c7dce30eef24c"

  private val serviceTemplateBodyV2 = "<h1>HMPPS Test Service V2</h1>"
  private val expectedServiceTemplateV2Hash = "ab5dfcf36828754c50e61b440a7b30acef43956579e02230f2d29c837a42a4cc"

  private val serviceConfig = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "hmpps-test-service",
    label = "HMPPS Test Service",
    order = 1,
    enabled = true,
    templateMigrated = true,
    url = "http://localhost:8080/",
  )

  private val publishedTemplateVersion = TemplateVersion(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
    status = TemplateVersionStatus.PUBLISHED,
    version = 1,
    createdAt = LocalDateTime.of(2025, 10, 11, 0, 0, 0),
    publishedAt = LocalDateTime.of(2025, 10, 11, 10, 0, 0),
    fileHash = expectedServiceTemplateV1Hash,
  )

  private val pendingTemplateVersion = TemplateVersion(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
    status = TemplateVersionStatus.PENDING,
    version = 2,
    createdAt = LocalDateTime.of(2025, 11, 11, 0, 0, 0),
    publishedAt = LocalDateTime.of(2025, 11, 11, 10, 0, 0),
    fileHash = expectedServiceTemplateV2Hash,
  )

  private val renderRequest = RenderRequest(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
  )

  private val templateVersionService = TemplateVersionService(
    serviceConfigurationService = serviceConfigurationService,
    templateVersionRepository = templateVersionRepository,
  )

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
        templateVersionService.verifyTemplateHash(renderRequest, serviceTemplateBodyV1)
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
    fun `should throw exception when update template version with status PENDING does not return updated count = 1`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV1Hash,
        returnValue = null,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV2Hash,
        returnValue = pendingTemplateVersion,
      )
      mockUpdateStatusAndPublishedAtByIdAndVersion(
        templateVersion = pendingTemplateVersion,
        returnValue = 0,
      )

      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, serviceTemplateBodyV2)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)
      assertThat(actual).hasMessageContaining("update template version status to PUBLISHED did not return expected result")
      assertThat(actual.params).containsAllEntriesOf(
        mapOf(
          "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
          "templateVersionId" to pendingTemplateVersion.id,
          "updated" to 0,
          "expectedUpdates" to 1,
        ),
      )

      verifyFindByIdAndEnabledAndTemplateMigratedCalled(
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV2Hash,
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV2Hash,
        times = 1,
      )
      verifyUpdateStatusAndPublishedAtByIdAndVersionCalled(
        id = pendingTemplateVersion.id,
        version = pendingTemplateVersion.version,
        times = 1,
      )
      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should throw exception when service template hash does not match any PUBLISHED or PENDING template versions`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV1Hash,
        returnValue = null,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV1Hash,
        returnValue = null,
      )

      val actual = assertThrows<SubjectAccessRequestServiceTemplateException> {
        templateVersionService.verifyTemplateHash(renderRequest, serviceTemplateBodyV1)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)
      assertThat(actual).hasMessageContaining("service template file hash does not match registered template versions")
      assertThat(actual.params).containsAllEntriesOf(
        mapOf(
          "serviceConfigurationId" to renderRequest.serviceConfiguration.id,
          "serviceTemplateHash" to expectedServiceTemplateV1Hash,
        ),
      )

      verifyFindByIdAndEnabledAndTemplateMigratedCalled(
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV1Hash,
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV1Hash,
        times = 1,
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
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV1Hash,
        returnValue = publishedTemplateVersion,
      )

      templateVersionService.verifyTemplateHash(renderRequest, serviceTemplateBodyV1)

      verifyFindByIdAndEnabledAndTemplateMigratedCalled(
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV1Hash,
        times = 1,
      )
      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should succeed and update template version to status PUBLISHED when service template hash matches template version hash with status PENDING`() {
      mockGetConfigurationById(
        serviceConfigurationId = serviceConfig.id,
        returnValue = serviceConfig,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV2Hash,
        returnValue = null,
      )
      mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV2Hash,
        returnValue = pendingTemplateVersion,
      )
      mockUpdateStatusAndPublishedAtByIdAndVersion(
        templateVersion = pendingTemplateVersion,
        returnValue = 1,
      )

      templateVersionService.verifyTemplateHash(renderRequest, serviceTemplateBodyV2)

      verifyFindByIdAndEnabledAndTemplateMigratedCalled(
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = expectedServiceTemplateV2Hash,
        times = 1,
      )
      verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
        status = TemplateVersionStatus.PENDING,
        fileHash = expectedServiceTemplateV2Hash,
        times = 1,
      )
      verifyUpdateStatusAndPublishedAtByIdAndVersionCalled(
        id = pendingTemplateVersion.id,
        version = pendingTemplateVersion.version,
        times = 1,
      )
      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }
  }

  private fun mockGetConfigurationById(
    serviceConfigurationId: UUID,
    returnValue: ServiceConfiguration?,
  ) {
    whenever(
      serviceConfigurationService.findByIdAndEnabledAndTemplateMigrated(
        id = serviceConfigurationId,
        enabled = true,
        templateMigrated = true,
      ),
    ).thenReturn(returnValue)
  }

  private fun mockFindLatestByServiceConfigurationIdAndStatusAndFileHash(
    status: TemplateVersionStatus,
    fileHash: String,
    returnValue: TemplateVersion?,
  ) {
    whenever(
      templateVersionRepository.findLatestByServiceConfigurationIdAndStatusAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        status = status,
        fileHash = fileHash,
      ),
    ).thenReturn(returnValue)
  }

  private fun mockUpdateStatusAndPublishedAtByIdAndVersion(
    templateVersion: TemplateVersion,
    returnValue: Int,
  ) {
    whenever(
      templateVersionRepository.updateStatusAndPublishedAtByIdAndVersion(
        id = eq(templateVersion.id),
        version = eq(templateVersion.version),
        newStatus = eq(TemplateVersionStatus.PUBLISHED),
        oldStatus = eq(TemplateVersionStatus.PENDING),
        publishedAt = any(),
      ),
    ).thenReturn(returnValue)
  }

  private fun verifyFindByIdAndEnabledAndTemplateMigratedCalled(
    times: Int,
  ) {
    verify(serviceConfigurationService, times(times)).findByIdAndEnabledAndTemplateMigrated(
      id = serviceConfig.id,
      enabled = true,
      templateMigrated = true,
    )
  }

  private fun verifyFindLatestByServiceConfigurationIdAndStatusAndFileHashCalled(
    status: TemplateVersionStatus,
    fileHash: String,
    times: Int,
  ) {
    verify(templateVersionRepository, times(times)).findLatestByServiceConfigurationIdAndStatusAndFileHash(
      serviceConfigurationId = serviceConfig.id,
      status = status,
      fileHash = fileHash,
    )
  }

  private fun verifyUpdateStatusAndPublishedAtByIdAndVersionCalled(
    id: UUID,
    version: Int,
    times: Int,
  ) {
    verify(templateVersionRepository, times(times)).updateStatusAndPublishedAtByIdAndVersion(
      eq(id),
      eq(version),
      eq(TemplateVersionStatus.PUBLISHED),
      eq(TemplateVersionStatus.PENDING),
      any(),
    )
  }

  @Nested
  inner class GetSha256HashValue {

    @Test
    fun `should return expected SHA-256 value`() {
      assertThat(templateVersionService.getSha256HashValue(serviceTemplateBodyV1))
        .isEqualTo(expectedServiceTemplateV1Hash)
    }
  }
}
