package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.ServiceConfigurationService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
abstract class TemplateVersionServiceTestFixture {

  protected val serviceConfigurationService: ServiceConfigurationService = mock()
  protected val templateVersionRepository: TemplateVersionRepository = mock()

  protected val publishedTemplateBody = "<h1>HMPPS Test Service</h1>"
  protected val publishedTemplateHash = "2340d53311fcf9aeaadeb6c90020d5ec77db229b342b0e0d088c7dce30eef24c"

  protected val pendingTemplateBody = "<h1>HMPPS Test Service V2</h1>"
  protected val pendingTemplateHash = "ab5dfcf36828754c50e61b440a7b30acef43956579e02230f2d29c837a42a4cc"

  protected val serviceConfig = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "hmpps-test-service",
    label = "HMPPS Test Service",
    order = 1,
    enabled = true,
    templateMigrated = true,
    url = "http://localhost:8080/",
  )

  protected val publishedTemplateVersion = TemplateVersion(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
    status = TemplateVersionStatus.PUBLISHED,
    version = 1,
    createdAt = LocalDateTime.of(2025, 10, 11, 0, 0, 0),
    publishedAt = LocalDateTime.of(2025, 10, 11, 10, 0, 0),
    fileHash = publishedTemplateHash,
  )

  protected val pendingTemplateVersion = TemplateVersion(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
    status = TemplateVersionStatus.PENDING,
    version = 2,
    createdAt = LocalDateTime.of(2025, 11, 11, 0, 0, 0),
    publishedAt = LocalDateTime.of(2025, 11, 11, 10, 0, 0),
    fileHash = pendingTemplateHash,
  )

  protected val renderRequest = RenderRequest(
    id = UUID.randomUUID(),
    serviceConfiguration = serviceConfig,
  )

  protected val templateVersionService = TemplateVersionService(
    serviceConfigurationService = serviceConfigurationService,
    templateVersionRepository = templateVersionRepository,
  )

  protected fun mockGetConfigurationById(
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

  protected fun mockFindLatestByServiceConfigurationIdAndFileHash(
    fileHash: String,
    returnValue: TemplateVersion?,
  ) {
    whenever(
      templateVersionRepository.findLatestByServiceConfigurationIdAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        fileHash = fileHash,
      ),
    ).thenReturn(returnValue)
  }

  protected fun mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
    id: UUID,
    version: Int,
    fileHash: String,
    status: TemplateVersionStatus = TemplateVersionStatus.PENDING,
    returnValue: TemplateVersion? = null,
  ) {
    whenever(
      templateVersionRepository.findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = id,
        version = version,
        fileHash = fileHash,
        status = status,
      ),
    ).thenReturn(returnValue)
  }

  protected fun mockSaveAllThrowsException() {
    whenever(templateVersionRepository.saveAndFlush(any()))
      .thenThrow(RuntimeException::class.java)
  }
}
