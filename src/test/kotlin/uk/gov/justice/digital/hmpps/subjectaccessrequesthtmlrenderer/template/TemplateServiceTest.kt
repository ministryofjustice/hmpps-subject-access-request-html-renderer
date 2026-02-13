package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest

class TemplateServiceTest {

  private val templateVersionService: TemplateVersionService = mock()
  private val templateVersionHealthService: TemplateVersionHealthService = mock()

  private var templateService: TemplateService = TemplateService(
    templateVersionService = templateVersionService,
    templateVersionHealthService = templateVersionHealthService,
  )

  private val data: Map<String, Any?> = mapOf(
    "Id" to 1234L,
  )

  @ParameterizedTest
  @CsvSource(
    value = [
      "court-case-service                       | <h1 class=\"title\">Prepare a Case for Sentence</h1>",
      "create-and-vary-a-licence-api            | <h1 class=\"title\">Create and vary a licence</h1>",
      "G1                                       | <h1 class=\"title\">G1</h1>",
      "G2                                       | <h1 class=\"title\">G2</h1>",
      "G3                                       | <h1 class=\"title\">G3</h1>",
      "hmpps-accredited-programmes-api          | <h1 class=\"title\">Accredited programmes</h1>",
      "hmpps-activities-management-api          | <h1 class=\"title\">Manage Activities and Appointments</h1>",
      "hmpps-approved-premises-api              | <h1 class=\"title\">Approved Premises</h1>",
      "hmpps-book-secure-move-api               | <h1 class=\"title\">Book a secure move</h1>",
      "hmpps-complexity-of-need                 | <h1 class=\"title\">Complexity of need</h1>",
      "hmpps-education-and-work-plan-api        | <h1 class=\"title\">Personal Learning Plan</h1>",
      "hmpps-education-employment-api           | <h1 class=\"title\">Work Readiness</h1>",
      "hmpps-hdc-api                            | <h1 class=\"title\">Home Detention Curfew</h1>",
      "hmpps-incentives-api                     | <h1 class=\"title\">Incentives</h1>",
      "hmpps-interventions-service              | <h1 class=\"title\">Refer and monitor an intervention</h1>",
      "hmpps-manage-adjudications-api           | <h1 class=\"title\">Manage Adjudications</h1>",
      "hmpps-non-associations-api               | <h1 class=\"title\">Non-associations</h1>",
      "hmpps-resettlement-passport-api          | <h1 class=\"title\">Prepare Someone for Release</h1>",
      "hmpps-restricted-patients-api            | <h1 class=\"title\">Restricted Patients</h1>",
      "hmpps-uof-data-api                       | <h1 class=\"title\">Use of force</h1>",
      "offender-management-allocation-manager   | <h1 class=\"title\">Manage Prison Offender Manager Cases</h1>",
    ],
    delimiterString = "|",
  )
  fun `should return expected render parameters when template migrated is false`(
    serviceName: String,
    expectedTitle: String,
  ) {
    val renderRequest = RenderRequest(
      serviceConfiguration = ServiceConfiguration(
        serviceName = serviceName,
        label = "HMPPS Test Service",
        enabled = true,
        templateMigrated = false,
        url = "https://example.com",
        category = ServiceCategory.PRISON,
      ),
    )
    val renderParameters = templateService.getRenderParameters(renderRequest, data)

    assertThat(renderParameters).isNotNull
    assertThat(renderParameters.templateVersion).isEqualTo("legacy")
    assertThat(renderParameters.template).contains(expectedTitle)
    assertThat(renderParameters.data).isEqualTo(data)

    verifyNoInteractions(templateVersionService)
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "court-case-service                       | <h1>Prepare a Case for Sentence</h1>  | 1.0.0",
      "create-and-vary-a-licence-api            | <h1>Create and vary a licence</h1>    | 1.0.1",
      "G1                                       | <h1>G1</h1>                           | 1.0.2",
      "G2                                       | <h1>G2</h1>                           | 1.0.3",
      "G3                                       | <h1>G3</h1>                           | 1.0.4",
      "hmpps-accredited-programmes-api          | <h1>Accredited programmes</h1>        | 1.0.5",
    ],
    delimiterString = "|",
  )
  fun `should return expected render parameters when template migrated is true`(
    serviceName: String,
    expectedTemplate: String,
    expectedVersion: String,
  ) {
    val renderRequest = RenderRequest(
      serviceConfiguration = ServiceConfiguration(
        serviceName = serviceName,
        label = "HMPPS Test Service",
        enabled = true,
        templateMigrated = true,
        url = "https://example.com",
        category = ServiceCategory.PRISON,
      ),
    )

    whenever(templateVersionService.getTemplate(renderRequest)).thenReturn(
      TemplateDetails(
        version = expectedVersion,
        body = expectedTemplate,
      ),
    )

    val renderParameters = templateService.getRenderParameters(renderRequest, data)

    assertThat(renderParameters).isNotNull
    assertThat(renderParameters.templateVersion).isEqualTo(expectedVersion)
    assertThat(renderParameters.template).contains(expectedTemplate)
    assertThat(renderParameters.data).isEqualTo(data)

    verify(templateVersionService, times(1)).getTemplate(renderRequest)
  }

  @Test
  fun `should return expected template when template is not migrated and no data is held`() {
    val renderRequest = RenderRequest(
      serviceConfiguration = ServiceConfiguration(
        serviceName = "court-case-service",
        label = "Court Case Service",
        enabled = true,
        templateMigrated = false,
        url = "https://example.com",
        category = ServiceCategory.PRISON,
      ),
    )

    val renderParameters = templateService.getRenderParameters(renderRequest, null)

    assertThat(renderParameters).isNotNull
    assertThat(renderParameters.templateVersion).isEqualTo("legacy")
    assertThat(renderParameters.template).isEqualTo("<h1 class=\"title\">{{serviceLabel}}</h1>\n<p>No Data Held</p>\n")
    assertThat(renderParameters.data).isEqualTo(mapOf("serviceLabel" to "Court Case Service"))

    verifyNoInteractions(templateVersionService)
  }

  @Test
  fun `should return expected params when template is migrated and no data is held`() {
    val renderRequest = RenderRequest(
      serviceConfiguration = ServiceConfiguration(
        serviceName = "offender-management-allocation-manager",
        label = "Manage Prison Offender Manager Cases",
        enabled = true,
        templateMigrated = true,
        url = "https://example.com",
        category = ServiceCategory.PRISON,
      ),
    )

    whenever(templateVersionService.getTemplate(renderRequest)).thenReturn(
      TemplateDetails(
        version = "1",
        body = "Some template value",
      ),
    )

    val renderParameters = templateService.getRenderParameters(renderRequest, null)

    assertThat(renderParameters).isNotNull
    assertThat(renderParameters.templateVersion).isEqualTo("1")
    assertThat(renderParameters.template).isEqualTo("<h1 class=\"title\">{{serviceLabel}}</h1>\n<p>No Data Held</p>\n")
    assertThat(renderParameters.data).isEqualTo(mapOf("serviceLabel" to "Manage Prison Offender Manager Cases"))

    verify(templateVersionService, times(1)).getTemplate(renderRequest)
  }

  @Test
  fun `should return style template`() {
    val styleTemplate = templateService.getStyleTemplate()

    assertThat(styleTemplate).isNotNull()
    assertThat(styleTemplate).isNotEmpty()
    assertThat(styleTemplate).contains("{{{ serviceTemplate }}}")
  }

  @Nested
  inner class TemplatesNotFoundTest {
    private val incorrectTemplateDir = "/not_templates_dir"
    private val templateService = TemplateService(
      templatesDirectory = incorrectTemplateDir,
      templateVersionService = templateVersionService,
      templateVersionHealthService = templateVersionHealthService,
    )

    @Test
    fun `should throw expected exception if requested template does not exist`() {
      val actual = assertThrows<SubjectAccessRequestException> {
        templateService.getRenderParameters(
          RenderRequest(
            serviceConfiguration = ServiceConfiguration(
              serviceName = "no-exist-service",
              label = "",
              enabled = true,
              templateMigrated = false,
              url = "https://example.com",
              category = ServiceCategory.PRISON,
            ),
          ),
          data = data,
        )
      }

      assertThat(actual.errorCode).isEqualTo(ErrorCode.TEMPLATE_RESOURCE_NOT_FOUND)
      assertThat(actual.message).startsWith("template resource not found")
      assertThat(actual.params).containsExactlyEntriesOf(
        mapOf("resource" to "$incorrectTemplateDir/template_no-exist-service.mustache"),
      )
    }

    @Test
    fun `should return empty string if style template not found`() {
      assertThat(templateService.getStyleTemplate()).isEmpty()
    }

    @Test
    fun `should throw expected exception when templateVersionService throws exception`() {
      val renderRequest = RenderRequest(
        serviceConfiguration = ServiceConfiguration(
          serviceName = "hmpps-test-service",
          label = "HMPPS Test Service",
          enabled = true,
          templateMigrated = true,
          url = "https://example.com",
          category = ServiceCategory.PRISON,
        ),
      )

      whenever(templateVersionService.getTemplate(renderRequest))
        .thenThrow(SubjectAccessRequestException::class.java)

      assertThrows<SubjectAccessRequestException> {
        templateService.getRenderParameters(renderRequest, data)
      }

      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }
  }
}
