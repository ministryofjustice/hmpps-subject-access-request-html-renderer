package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_CONFIGURATION_NOT_FOUND
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_EMPTY
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_HASH_MISMATCH
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_PUBLISHED
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.RenderEvent.SERVICE_TEMPLATE_PUBLISH_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.HealthStatusType
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus

class TemplateVersionServiceTest : TemplateVersionServiceTestFixture() {

  @Nested
  inner class GetSha256HashValue {

    @Test
    fun `should return expected SHA-256 value`() {
      assertThat(templateVersionService.getSha256HashValue(v1PublishedBody))
        .isEqualTo(v1PublishedHash)
    }
  }

  @Nested
  inner class GetTemplateError {

    @Test
    fun `should throw exception if service template is empty`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(""),
      )

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest = renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "service template hash error: service template was empty",
        errorCode = ErrorCode.SERVICE_TEMPLATE_EMPTY,
        params = mapOf("serviceConfigurationId" to serviceConfig.id),
      )

      verify(dynamicServicesClient, times(1)).getServiceTemplate(renderRequest)

      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_EMPTY,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
      )
    }

    @Test
    fun `should throw exception if dynamicServicesClient get template throws exception`() {
      whenever(dynamicServicesClient.getServiceTemplate(renderRequest))
        .thenThrow(SubjectAccessRequestRetryExhaustedException::class.java)

      assertThrows<SubjectAccessRequestRetryExhaustedException> {
        templateVersionService.getTemplate(renderRequest = renderRequest)
      }

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
    }

    @Test
    fun `should throw exception when service configuration does not exist`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v1PublishedBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = null,
      )

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "service configuration not found matching id, templateMigrated=true, and enabled=true",
        errorCode = ErrorCode.SERVICE_CONFIGURATION_NOT_FOUND,
        params = mapOf("serviceConfigurationId" to serviceConfig.id),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
      verifyTelemetryEventsCaptures(
        event = SERVICE_CONFIGURATION_NOT_FOUND,
        subjectAccessRequestId = renderRequest.id,
        "serviceConfigurationId" to serviceConfig.id.toString(),
      )
    }

    @Test
    fun `should throw exception when service template does not match any registered template versions`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v3UnregisteredBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = v1Published)
      templateVersionRepository.mockFindLatestPendingByServiceConfigurationId(returnValue = v2Pending)

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "service template file hash does not match registered template versions",
        errorCode = ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH,
        params = mapOf(
          "serviceConfigurationId" to serviceConfig.id,
          "serviceTemplateHash" to v3UnregisteredHash,
        ),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdIsCalled(times = 1)
      templateVersionHealthService.verifyUpdateHealthStatusIfChangedIsCalled(
        times = 1,
        serviceConfiguration = serviceConfig,
        HealthStatusType.UNHEALTHY,
      )
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_HASH_MISMATCH,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "serviceConfigurationId" to serviceConfig.id.toString(),
        "serviceTemplateHash" to v3UnregisteredHash,
      )
    }

    @Test
    fun `should throw exception when no template version exists for status PENDING or PUBLISHED`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v3UnregisteredBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = null)
      templateVersionRepository.mockFindLatestPendingByServiceConfigurationId(returnValue = null)

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "service template file hash does not match registered template versions",
        errorCode = ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH,
        params = mapOf(
          "serviceConfigurationId" to serviceConfig.id,
          "serviceTemplateHash" to v3UnregisteredHash,
        ),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdIsCalled(times = 1)
      templateVersionHealthService.verifyUpdateHealthStatusIfChangedIsCalled(
        times = 1,
        serviceConfiguration = serviceConfig,
        HealthStatusType.UNHEALTHY,
      )
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_HASH_MISMATCH,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "serviceConfigurationId" to serviceConfig.id.toString(),
        "serviceTemplateHash" to v3UnregisteredHash,
      )
    }

    @Test
    fun `should throw exception if error is thrown when updating a template status from PENDING to PUBLISHED`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()

      dynamicServicesClient.mockGetServiceTemplate(returnValue = ResponseEntity.ok(v2PendingBody))
      serviceConfigurationService.mockGetConfigurationById(returnValue = serviceConfig)
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = null)
      templateVersionRepository.mockFindLatestPendingByServiceConfigurationId(returnValue = v2Pending)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = v2Pending.id,
        version = v2Pending.version,
        fileHash = v2PendingHash,
        returnValue = v2Pending,
      )

      templateVersionRepository.mockSaveAndFlushException()

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "unexpected error whilst attempting to publish template version",
        errorCode = ErrorCode.SERVICE_TEMPLATE_PUBLISH_FAILURE,
        params = mapOf(
          "serviceName" to serviceConfig.serviceName,
          "version" to v2Pending.version,
          "templateVersionId" to v2Pending.id,
        ),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = v2PendingHash,
      )
      templateVersionRepository.verifySaveAndFlushIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = v2PendingHash,
        captor = saveCaptor,
      )
      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_PUBLISH_ERROR,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "version" to v2Pending.version.toString(),
        "templateVersionId" to v2Pending.id.toString(),
      )
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }
  }

  @Nested
  inner class GetTemplateSuccess {

    @Test
    fun `should succeed when service template hash matches PUBLISHED template version hash`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v1PublishedBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = v1Published)

      templateVersionService.getTemplate(renderRequest)

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdNeverCalled()
      templateVersionHealthService.verifyUpdateHealthStatusIfChangedIsCalled(
        times = 1,
        serviceConfiguration = serviceConfig,
        HealthStatusType.HEALTHY,
      )

      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should succeed and update template version to status PUBLISHED when service template hash matches template version hash with status PENDING`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()

      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v2PendingBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = null)
      templateVersionRepository.mockFindLatestPendingByServiceConfigurationId(returnValue = v2Pending)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = v2Pending.id,
        version = 2,
        fileHash = v2PendingHash,
        status = TemplateVersionStatus.PENDING,
        returnValue = v2Pending,
      )

      templateVersionService.getTemplate(renderRequest)

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = v2Pending.id,
        version = v2Pending.version,
        fileHash = v2PendingHash,
        returnValue = v2Pending,
      )
      templateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = v2PendingHash,
      )
      templateVersionRepository.verifySaveAndFlushIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = v2PendingHash,
        captor = saveCaptor,
      )

      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_PUBLISHED,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "version" to v2Pending.version.toString(),
      )

      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should succeed when service template matches PUBLISHED template and there is a PENDING template created after the PUBLISHED template`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v1PublishedBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = v1Published)

      val actual = templateVersionService.getTemplate(renderRequest)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(TemplateDetails(version = "1", body = v1PublishedBody))

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdNeverCalled()

      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }


    @Test
    fun `should succeed when service template matches PENDING template created after a previous PUBLISHED template`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(v2PendingBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestPublishedByServiceConfigurationId(returnValue = v1Published)
      templateVersionRepository.mockFindLatestPendingByServiceConfigurationId(returnValue = v2Pending)
      templateVersionRepository. mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = v2PendingHash,
        returnValue = v2Pending,
      )

      val actual = templateVersionService.getTemplate(renderRequest)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(TemplateDetails(version = "2", body = v2PendingBody))

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPublishedByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestPendingByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = v2PendingHash,
      )
      templateVersionRepository.verifySaveAndFlushIsCalled(
        times = 1,
        templateVersionId = v2Pending.id,
        version = 2,
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = v2PendingHash,
        captor = saveCaptor,
      )
      templateVersionHealthService.verifyUpdateHealthStatusIfChangedIsCalled(
        times = 1,
        serviceConfiguration = serviceConfig,
        HealthStatusType.HEALTHY,
      )

      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }
  }
}
