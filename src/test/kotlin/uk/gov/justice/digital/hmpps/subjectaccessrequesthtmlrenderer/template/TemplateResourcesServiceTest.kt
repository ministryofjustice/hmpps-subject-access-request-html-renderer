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
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest

class TemplateResourcesServiceTest {

  private val templateVersionService: TemplateVersionService = mock()

  private var templateResourcesService = TemplateResourcesService(
    templateVersionService = templateVersionService,
  )

  @ParameterizedTest
  @CsvSource(
    value = [
      "court-case-service                       | <h1>Prepare a Case for Sentence</h1>",
      "create-and-vary-a-licence-api            | <h1>Create and vary a licence</h1>",
      "G1                                       | <h1>G1</h1>",
      "G2                                       | <h1>G2</h1>",
      "G3                                       | <h1>G3</h1>",
      "hmpps-accredited-programmes-api          | <h1>Accredited programmes</h1>",
      "hmpps-activities-management-api          | <h1>Manage Activities and Appointments</h1>",
      "hmpps-approved-premises-api              | <h1>Approved Premises</h1>",
      "hmpps-book-secure-move-api               | <h1>Book a secure move</h1>",
      "hmpps-complexity-of-need                 | <h1>Complexity of need</h1>",
      "hmpps-education-and-work-plan-api        | <h1>Personal Learning Plan</h1>",
      "hmpps-education-employment-api           | <h1>Work Readiness</h1>",
      "hmpps-hdc-api                            | <h1>Home Detention Curfew</h1>",
      "hmpps-incentives-api                     | <h1>Incentives</h1>",
      "hmpps-interventions-service              | <h1>Refer and monitor an intervention</h1>",
      "hmpps-manage-adjudications-api           | <h1>Manage Adjudications</h1>",
      "hmpps-non-associations-api               | <h1>Non-associations</h1>",
      "hmpps-resettlement-passport-api          | <h1>Prepare Someone for Release</h1>",
      "hmpps-restricted-patients-api            | <h1>Restricted Patients</h1>",
      "hmpps-uof-data-api                       | <h1>Use of force</h1>",
      "offender-management-allocation-manager   | <h1>Manage Prison Offender Manager Cases</h1>",
    ],
    delimiterString = "|",
  )
  fun `should return expected service template`(serviceName: String, expectedTitle: String) {
    val renderRequest = RenderRequest(
      serviceConfiguration = ServiceConfiguration(
        serviceName = serviceName,
        label = "HMPPS Test Service",
        order = 1,
        enabled = true,
        templateMigrated = false,
        url = "https://example.com",
      ),
    )
    val testTemplate = templateResourcesService.getServiceTemplate(renderRequest)
    assertThat(testTemplate).isNotNull()
    assertThat(testTemplate).contains(expectedTitle)

    verifyNoInteractions(templateVersionService)
  }

  @Test
  fun `should return style template`() {
    val styleTemplate = templateResourcesService.getStyleTemplate()

    assertThat(styleTemplate).isNotNull()
    assertThat(styleTemplate).isNotEmpty()
    assertThat(styleTemplate).contains("{{{ serviceTemplate }}}")
  }

  @Nested
  inner class TemplatesNotFoundTest {
    private val incorrectTemplateDir = "/not_templates_dir"
    private val templateResourcesService = TemplateResourcesService(
      templatesDirectory = incorrectTemplateDir,
      templateVersionService = templateVersionService,
    )

    @Test
    fun `should throw expected exception if requested template does not exist`() {
      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        templateResourcesService.getServiceTemplate(
          RenderRequest(
            serviceConfiguration = ServiceConfiguration(
              serviceName = "no-exist-service",
              label = "",
              order = 1,
              enabled = true,
              templateMigrated = false,
              url = "https://example.com",
            ),
          ),
        )
      }

      assertThat(actual.message).startsWith("template resource not found")
      assertThat(actual.params).containsExactlyEntriesOf(
        mapOf("resource" to "$incorrectTemplateDir/template_no-exist-service.mustache"),
      )
    }

    @Test
    fun `should return empty string if style template not found`() {
      assertThat(templateResourcesService.getStyleTemplate()).isEmpty()
    }
  }

  @Nested
  inner class TemplateMigratedTest {

    private val serviceConfiguration = ServiceConfiguration(
      serviceName = "hmpps-test-service",
      label = "HMPPS Test Service",
      order = 1,
      enabled = true,
      templateMigrated = true,
      url = "https://example.com",
    )

    private val renderRequest = RenderRequest(serviceConfiguration = serviceConfiguration)

    @Test
    fun `should return expected template when service configuration templateMigrated is true`() {
      whenever(templateVersionService.getTemplate(renderRequest))
        .thenReturn("<h1>HMPPS Test Service - Migrated Template</h1>")

      val actual = templateResourcesService.getServiceTemplate(renderRequest)

      assertThat(actual).isEqualTo("<h1>HMPPS Test Service - Migrated Template</h1>")

      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }

    @Test
    fun `should throw expected exception when templateVersionService throws exception`() {
      whenever(templateVersionService.getTemplate(renderRequest))
        .thenThrow(SubjectAccessRequestTemplatingException::class.java)

      assertThrows<SubjectAccessRequestTemplatingException> {
        templateResourcesService.getServiceTemplate(renderRequest)
      }

      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }
  }
}
