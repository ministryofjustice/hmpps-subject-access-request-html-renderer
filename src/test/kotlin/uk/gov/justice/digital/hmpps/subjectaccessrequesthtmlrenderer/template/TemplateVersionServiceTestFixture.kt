package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
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
  protected val dynamicServicesClient: DynamicServicesClient = mock()

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
    dynamicServicesClient = dynamicServicesClient,
  )

  protected fun ServiceConfigurationService.mockGetConfigurationById(
    serviceConfigurationId: UUID = serviceConfig.id,
    returnValue: ServiceConfiguration?,
  ) {
    whenever(
      this.findByIdAndEnabledAndTemplateMigrated(
        id = serviceConfigurationId,
        enabled = true,
        templateMigrated = true,
      ),
    ).thenReturn(returnValue)
  }

  protected fun ServiceConfigurationService.verifyGetServiceConfigurationIsCalled(
    times: Int = 1,
    serviceConfigurationId: UUID = serviceConfig.id,
    enabled: Boolean = true,
    templateMigrated: Boolean = true,
  ) {
    verify(this, times(times)).findByIdAndEnabledAndTemplateMigrated(
      id = serviceConfigurationId,
      enabled = enabled,
      templateMigrated = templateMigrated,
    )
  }

  protected fun TemplateVersionRepository.mockFindLatestByServiceConfigurationIdAndFileHash(
    fileHash: String,
    returnValue: TemplateVersion?,
  ) {
    whenever(
      this.findLatestByServiceConfigurationIdAndFileHash(
        serviceConfigurationId = serviceConfig.id,
        fileHash = fileHash,
      ),
    ).thenReturn(returnValue)
  }

  protected fun TemplateVersionRepository.verifyFindLatestByServiceConfigurationIdAndFileHashIsCalled(
    times: Int = 1,
    serviceConfigurationId: UUID = serviceConfig.id,
    fileHash: String,
  ) {
    verify(this, times(times)).findLatestByServiceConfigurationIdAndFileHash(
      serviceConfigurationId = serviceConfigurationId,
      fileHash = fileHash,
    )
  }

  protected fun TemplateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
    id: UUID,
    version: Int,
    fileHash: String,
    status: TemplateVersionStatus = TemplateVersionStatus.PENDING,
    returnValue: TemplateVersion? = null,
  ) {
    whenever(
      this.findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = id,
        version = version,
        fileHash = fileHash,
        status = status,
      ),
    ).thenReturn(returnValue)
  }

  protected fun TemplateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
    times: Int = 1,
    templateVersionId: UUID,
    version: Int,
    status: TemplateVersionStatus,
    fileHash: String,
  ) {
    verify(this, times(times))
      .findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = templateVersionId,
        version = version,
        status = status,
        fileHash = fileHash,
      )
  }

  protected fun TemplateVersionRepository.mockSaveAndFlushException() {
    whenever(this.saveAndFlush(any()))
      .thenThrow(RuntimeException::class.java)
  }

  protected fun TemplateVersionRepository.verifySaveAndFlushIsCalled(
    times: Int = 1,
    templateVersionId: UUID,
    version: Int,
    status: TemplateVersionStatus,
    fileHash: String,
    captor: KArgumentCaptor<TemplateVersion>,
  ) {
    verify(this, times(times)).saveAndFlush(captor.capture())

    assertThat(captor.firstValue.id).isEqualTo(templateVersionId)
    assertThat(captor.firstValue.version).isEqualTo(version)
    assertThat(captor.firstValue.status).isEqualTo(status)
    assertThat(captor.firstValue.fileHash).isEqualTo(fileHash)
  }

  protected fun DynamicServicesClient.mockGetServiceTemplate(
    expectedRequest: RenderRequest = renderRequest,
    returnValue: ResponseEntity<String>?,
  ) {
    whenever(this.getServiceTemplate(expectedRequest)).thenReturn(returnValue)
  }

  protected fun <T : SubjectAccessRequestException> assertIsExpectedException(
    actual: T,
    subjectAccessRequestId: UUID? = renderRequest.id!!,
    message: String,
    params: Map<String, *>? = null,
  ) {
    assertThat(actual).isNotNull()
    assertThat(actual.subjectAccessRequestId).isEqualTo(subjectAccessRequestId)
    assertThat(actual.message).startsWith(message)
    if (params == null) {
      assertThat(actual.params).isNull()
    } else {
      assertThat(actual.params).containsAllEntriesOf(params)
    }
  }

  protected fun DynamicServicesClient.verifyGetServiceTemplateIsCalled(
    times: Int = 1,
    expectedRequest: RenderRequest = renderRequest,
  ) {
    verify(this, times(times)).getServiceTemplate(expectedRequest)
  }
}
