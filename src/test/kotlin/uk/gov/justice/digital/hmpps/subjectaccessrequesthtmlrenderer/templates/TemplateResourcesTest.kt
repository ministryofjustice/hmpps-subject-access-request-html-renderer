package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException

class TemplateResourcesTest {

  private var templateResources = TemplateResources()

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
    val renderRequest = RenderRequest(serviceName = serviceName)
    val testTemplate = templateResources.getServiceTemplate(renderRequest)
    assertThat(testTemplate).isNotNull()
    assertThat(testTemplate).contains(expectedTitle)
  }

  @Test
  fun `should return style template`() {
    val styleTemplate = templateResources.getStyleTemplate()

    assertThat(styleTemplate).isNotNull()
    assertThat(styleTemplate).isNotEmpty()
    assertThat(styleTemplate).contains("{{{ serviceTemplate }}}")
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "     | service name is null",
      " ''  | service name is empty",
    ],
    delimiterString = "|",
  )
  fun `should throw service name missing exception when render request serviceName is missing`(
    serviceName: String?,
    description: String,
  ) {
    val actual = assertThrows<SubjectAccessRequestTemplatingException> {
      templateResources.getServiceTemplate(RenderRequest(serviceName = serviceName))
    }

    assertThat(actual.message).startsWith("unable to load service template: service name was null or empty")
  }

  @Nested
  inner class TemplatesNotFoundTest {
    private val incorrectTemplateDir = "/not_templates_dir"
    private val templateResources = TemplateResources(templatesDirectory = incorrectTemplateDir)

    @Test
    fun `should throw expected exception if requested template does not exist`() {
      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        templateResources.getServiceTemplate(RenderRequest(serviceName = "no-exist-service"))
      }

      assertThat(actual.message).startsWith("template resource not found")
      assertThat(actual.params).containsExactlyEntriesOf(
        mapOf("resource" to "$incorrectTemplateDir/template_no-exist-service.mustache"),
      )
    }

    @Test
    fun `should return empty string if style template not found`() {
      assertThat(templateResources.getStyleTemplate()).isEmpty()
    }
  }
}
