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
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus

class TemplateVersionServiceTest : TemplateVersionServiceTestFixture() {

  @Nested
  inner class GetSha256HashValue {

    @Test
    fun `should return expected SHA-256 value`() {
      assertThat(templateVersionService.getSha256HashValue(publishedTemplateBody))
        .isEqualTo(publishedTemplateHash)
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
        returnValue = ResponseEntity.ok(publishedTemplateBody),
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
        returnValue = ResponseEntity.ok(publishedTemplateBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestByServiceConfigurationId(returnValue = null)

      val actual = assertThrows<SubjectAccessRequestException> {
        templateVersionService.getTemplate(renderRequest)
      }

      assertIsExpectedException(
        actual = actual,
        message = "service template file hash does not match registered template versions",
        errorCode = ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH,
        params = mapOf(
          "serviceConfigurationId" to serviceConfig.id,
          "serviceTemplateHash" to publishedTemplateHash,
        ),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestByServiceConfigurationIdIsCalled(times = 1)
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_HASH_MISMATCH,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "serviceConfigurationId" to serviceConfig.id.toString(),
        "serviceTemplateHash" to publishedTemplateHash,
      )
    }

    @Test
    fun `should throw exception if error is thrown when updating a template status from PENDING to PUBLISHED`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()

      dynamicServicesClient.mockGetServiceTemplate(returnValue = ResponseEntity.ok(pendingTemplateBody))
      serviceConfigurationService.mockGetConfigurationById(returnValue = serviceConfig)
      templateVersionRepository.mockFindLatestByServiceConfigurationId(returnValue = pendingTemplateVersion)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = pendingTemplateVersion.version,
        fileHash = pendingTemplateHash,
        returnValue = pendingTemplateVersion,
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
          "version" to pendingTemplateVersion.version,
          "templateVersionId" to pendingTemplateVersion.id,
        ),
      )

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
        times = 1,
        templateVersionId = pendingTemplateVersion.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = pendingTemplateHash,
      )
      templateVersionRepository.verifySaveAndFlushIsCalled(
        times = 1,
        templateVersionId = pendingTemplateVersion.id,
        version = 2,
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = pendingTemplateHash,
        captor = saveCaptor,
      )
      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_PUBLISH_ERROR,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "version" to pendingTemplateVersion.version.toString(),
        "templateVersionId" to pendingTemplateVersion.id.toString(),
      )
      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }
  }

  @Nested
  inner class GetTemplateSuccess {

    @Test
    fun `should succeed when service template hash matches PUBLISHED template version hash`() {
      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(publishedTemplateBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestByServiceConfigurationId(returnValue = publishedTemplateVersion)

      templateVersionService.getTemplate(renderRequest)

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestByServiceConfigurationIdIsCalled(times = 1)

      verifyNoMoreInteractions(dynamicServicesClient, templateVersionRepository, serviceConfigurationService)
    }

    @Test
    fun `should succeed and update template version to status PUBLISHED when service template hash matches template version hash with status PENDING`() {
      val saveCaptor = argumentCaptor<TemplateVersion>()

      dynamicServicesClient.mockGetServiceTemplate(
        returnValue = ResponseEntity.ok(pendingTemplateBody),
      )
      serviceConfigurationService.mockGetConfigurationById(
        returnValue = serviceConfig,
      )
      templateVersionRepository.mockFindLatestByServiceConfigurationId(returnValue = pendingTemplateVersion)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = 2,
        fileHash = pendingTemplateHash,
        status = TemplateVersionStatus.PENDING,
        returnValue = pendingTemplateVersion,
      )

      templateVersionService.getTemplate(renderRequest)

      dynamicServicesClient.verifyGetServiceTemplateIsCalled(times = 1)
      serviceConfigurationService.verifyGetServiceConfigurationIsCalled(times = 1)
      templateVersionRepository.verifyFindLatestByServiceConfigurationIdIsCalled(times = 1)
      templateVersionRepository.mockFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = pendingTemplateVersion.id,
        version = pendingTemplateVersion.version,
        fileHash = pendingTemplateHash,
        returnValue = pendingTemplateVersion,
      )
      templateVersionRepository.verifyFindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDescIsCalled(
        times = 1,
        templateVersionId = pendingTemplateVersion.id,
        version = 2,
        status = TemplateVersionStatus.PENDING,
        fileHash = pendingTemplateHash,
      )
      templateVersionRepository.verifySaveAndFlushIsCalled(
        times = 1,
        templateVersionId = pendingTemplateVersion.id,
        version = 2,
        status = TemplateVersionStatus.PUBLISHED,
        fileHash = pendingTemplateHash,
        captor = saveCaptor,
      )

      verifyTelemetryEventsCaptures(
        event = SERVICE_TEMPLATE_PUBLISHED,
        subjectAccessRequestId = renderRequest.id,
        "serviceName" to serviceConfig.serviceName,
        "version" to pendingTemplateVersion.version.toString(),
      )

      verifyNoMoreInteractions(templateVersionRepository, serviceConfigurationService)
    }
  }
}
