package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository

class TemplateRenderingServiceTest : TemplateRenderingServiceTestFixture() {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val templateVersionService: TemplateVersionService = mock()
  private val templateVersionHealthService: TemplateVersionHealthService = mock()
  private val expectedTemplateVersion = "legacy"

  private val templateDataFetcherFacade = TemplateDataFetcherFacadeImpl(
    prisonDetailsRepository,
    userDetailsRepository,
    locationDetailsRepository,
    locationsApiClient,
    nomisMappingApiClient,
  )
  private val templateHelpers = TemplateHelpers(templateDataFetcherFacade)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val templateRenderingService = TemplateRenderingService(
    templateRenderService,
    TemplateService(
      templatesDirectory = "/templates",
      templateVersionService = templateVersionService,
      templateVersionHealthService = templateVersionHealthService,
    ),
    telemetryClient,
  )

  private fun renderServiceDataHtml(serviceName: String, data: Any?): RenderedHtml {
    val actual = templateRenderingService.renderServiceDataHtml(
      RenderRequest(
        serviceConfiguration = ServiceConfiguration(
          serviceName = serviceName,
          label = serviceName,
          url = "",
          enabled = true,
          templateMigrated = false,
          category = ServiceCategory.PRISON,
        ),
      ),
      data,
    )
    assertThat(actual).isNotNull()
    return actual
  }

  @Nested
  inner class LegacyTemplates {

    @Test
    fun `renderTemplate renders a style template given a service template`() {
      whenever(prisonDetailsRepository.findByPrisonId("SPT")).thenReturn(
        PrisonDetail(
          prisonId = "SPT",
          prisonName = "Springfield Penitentiary",
        ),
      )

      val actual = renderServiceDataHtml("test-service", testServiceTemplateData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()

      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).isNotEmpty()
      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td>Test Key:</td><td>testValue</td>")
      assertThat(renderedTemplate).contains("<td>Prison: </td><td>Springfield Penitentiary</td>")
      assertThat(renderedTemplate).contains("<td>Nested Data:</td><td>nestedValue</td>")
      assertThat(renderedTemplate).contains("<td>Array Data:</td><td><ul><li>arrayValue1-1</li><li>arrayValue1-2</li></ul></td>")
      assertThat(renderedTemplate).contains("<td>Test Key:</td><td>testValue2</td>")
      assertThat(renderedTemplate).contains("<td>Nested Data:</td><td>nestedValue2</td>")
      assertThat(renderedTemplate).contains("<td>Array Data:</td><td><ul><li>arrayValue2-1</li><li>arrayValue2-2</li></ul></td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a keyworker template`() {
      val actual = renderServiceDataHtml("keyworker-api", testKeyworkerServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.data).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td>Allocated at</td><td>03 December 2019, 11:00:58 am</td>")
      assertThat(renderedTemplate).contains("<td>Allocation is active</td><td>No</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given an activities template`() {
      val actual = renderServiceDataHtml("hmpps-activities-management-api", testActivitiesServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">End date</td>")
      assertThat(renderedTemplate).contains("<h3>Application - Waiting list ID 1</h3>")
      assertThat(renderedTemplate).contains("<h3>Application - Waiting list ID 10</h3>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Status date</td>")
      assertThat(renderedTemplate).contains("<h3>Appointment</h3>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given an incentives template`() {
      whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(
        PrisonDetail(
          "MDI",
          "Moorland (HMP & YOI)",
        ),
      )

      val actual = renderServiceDataHtml("hmpps-incentives-api", testIncentivesServiceData)
      assertThat(actual.data).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td>03 December 2019</td>")
      assertThat(renderedTemplate).contains("<td>03 July 2023, 9:14:25 pm</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given an adjudications template`() {
      val actual = renderServiceDataHtml("hmpps-manage-adjudications-api", testAdjudicationsServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td>Date and time of incident</td><td>08 June 2023, 12:00:00 pm</td>")
      assertThat(renderedTemplate).contains("<td>Description</td><td>Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:</td>")
      assertThat(renderedTemplate).contains("<td>Description</td><td>Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own</td>")
      assertThat(renderedTemplate).contains("<td>Status</td><td>CHARGE_PROVED</td>")
      assertThat(renderedTemplate).contains("<td>ELECTRICAL_REPAIR</td>")
      assertThat(renderedTemplate).contains("<td>BAGGED_AND_TAGGED</td>")
      assertThat(renderedTemplate).contains("<td>OIC hearing type</td><td>INAD_ADULT</td>")
      assertThat(renderedTemplate).contains("<td>James Warburton</td>")
      assertThat(renderedTemplate).contains("<td>Code</td><td>CHARGE_PROVED</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-30\">Privilege type</td>")
      assertThat(renderedTemplate).contains("<td>Linked charge numbers</td><td>9872-1, 9872-2</td>")
      assertThat(renderedTemplate).contains("<td>DAYS</td>")
      assertThat(renderedTemplate).contains("<td>Some info</td>")
      assertThat(renderedTemplate).contains("<td>Reason for change</td><td>APPEAL</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a home detentions curfew template`() {
      val actual = renderServiceDataHtml("hmpps-hdc-api", testHDCServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Vary version</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Has considered checks</td>")
      assertThat(renderedTemplate).contains("<tr><td>First night from</td><td>15:00</td></tr>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-15\">Friday from</td>")
      assertThat(renderedTemplate).contains("<td>Decision maker</td><td>Test User</td>")
      assertThat(renderedTemplate).contains("<td>Offence committed before Feb 2015</td><td>No</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Telephone</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Curfew address line 1</td>")
      assertThat(renderedTemplate).contains("<tr><td>Bass requested</td><td>Yes</td></tr>")
      assertThat(renderedTemplate).contains("<td>Additional conditions required</td><td>No</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a Use of Force template`() {
      whenever(userDetailsRepository.findByUsername("ZANDYUSER_ADM")).thenReturn(
        UserDetail(
          "ZANDYUSER_ADM",
          "Lee",
        ),
      )

      val actual = renderServiceDataHtml("hmpps-uof-data-api", testUseOfForceServiceData)
      assertThat(actual.data).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).isNotNull()
      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-50\">Incident date</td>")
      assertThat(renderedTemplate).contains("<td>CCTV recording</td>")
      assertThat(renderedTemplate).contains("<td>Name</td><td>Lee</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-25\">Baton drawn</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a prepare someone for release template`() {
      val actual = renderServiceDataHtml(
        "hmpps-resettlement-passport-api",
        testResettlementPassportServiceData,
      )

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("Prison name")
      assertThat(renderedTemplate).contains("Deed poll certificate")
      assertThat(renderedTemplate).contains("Account opened")
      assertThat(renderedTemplate).contains("FINANCE_AND_ID")
      assertThat(renderedTemplate).contains("Date application submitted")
      assertThat(renderedTemplate).contains("James Boobier")
      assertThat(renderedTemplate).contains("DRUGS_AND_ALCOHOL")
      assertThat(renderedTemplate).contains("Help finding accomodation")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a court case service template`() {
      val actual = renderServiceDataHtml("court-case-service", testCourtCaseServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h5>Notes</h5>")
      assertThat(renderedTemplate).contains("This is a note")
      assertThat(renderedTemplate).contains("<h5>Outcomes</h5>")
      assertThat(renderedTemplate).contains("ADJOURNED")
      assertThat(renderedTemplate).contains("<h3>Comments</h3>")
      assertThat(renderedTemplate).contains("Author One")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a accredited programme service template`() {
      val actual = renderServiceDataHtml(
        "hmpps-accredited-programmes-api",
        testAccreditedProgrammesServiceData,
      )

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h2>Referrals</h2>")
      assertThat(renderedTemplate).contains("<td>Becoming New Me Plus</td>")
      assertThat(renderedTemplate).contains("<td>12 March 2024, 2:23:12 pm</td>")
      assertThat(renderedTemplate).contains("<td>Kaizen</td>")
      assertThat(renderedTemplate).contains("<td>AELANGOVAN_ADM</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a Non-associations template`() {
      val actual = renderServiceDataHtml("hmpps-non-associations-api", testNonAssociationsServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h3>Non-association - ID 83493</h3>")
      assertThat(renderedTemplate).contains("Restriction type")
      assertThat(renderedTemplate).contains("This is a test for SAR")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a Interventions Service template`() {
      val actual = renderServiceDataHtml("hmpps-interventions-service", testInterventionsServiceData)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h2>Referrals</h2>")
      assertThat(renderedTemplate).contains("<tr><td>Referral number</td><td>JE2862AC</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Late reason</td><td>SAR Test 21 - Add how late they were and anything you know about the reason.</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Referral number</td><td>FY7705FI</td></tr>")
      assertThat(renderedTemplate).contains("<p>No Data Held</p>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a Categorisation Service template`() {
      val actual = renderServiceDataHtml(
        "hmpps-offender-categorisation-api",
        testCategorisationServiceData,
      )

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h3>Categorisation form</h3>")
      assertThat(renderedTemplate).contains("<tr><td>Text</td><td>previous terrorism offences text - talking about bombs</td></tr>")
      assertThat(renderedTemplate).contains("<h5>Ratings</h5>")
      assertThat(renderedTemplate).contains("<tr><td>Risk information last updated</td><td>27 July 2021, 2:17:48 am</td></tr>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given a complexity of need template`() {
      val actual = renderServiceDataHtml(
        "hmpps-complexity-of-need",
        loadServiceFixture("hmpps-complexity-of-need"),
      )

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<h2>Complexity</h2>")
      assertThat(renderedTemplate).contains("<tr><td>Level</td><td>low</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Active</td><td>Yes</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Creation</td><td>30 March 2021, 11:45:10 am</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Updated</td><td>30 March 2021, 7:54:46 pm</td></tr>")
      assertThat(renderedTemplate).contains("<tr><td>Notes</td><td>string</td></tr>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate renders a template given an offender management allocation manager template`() {
      whenever(prisonDetailsRepository.findByPrisonId("LEI")).thenReturn(
        PrisonDetail(
          "LEI",
          "Leeds (HMP)",
        ),
      )

      val actual = renderServiceDataHtml(
        "offender-management-allocation-manager",
        loadServiceFixture("offender-management-allocation-manager"),
      )

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-35\">Event</td>")
      assertThat(renderedTemplate).contains("<td>Allocate primary POM</td>")
      assertThat(renderedTemplate).contains("<td class=\"data-column-35\">Recommended prison or probation offender manager type</td>")
      assertThat(renderedTemplate).contains("<td>Prison POM</td>")

      // Main sections
      assertThat(renderedTemplate).contains("<h3>Allocation history</h3>")
      assertThat(renderedTemplate).contains("<h3>Calculated early allocation status</h3>")
      assertThat(renderedTemplate).contains("<h3>Calculated handover date</h3>")
      assertThat(renderedTemplate).contains("<h3>Case information</h3>")
      assertThat(renderedTemplate).contains("<h3>Early allocations</h3>")
      assertThat(renderedTemplate).contains("<h3>Handover progress checklist</h3>")
      assertThat(renderedTemplate).contains("<h3>Responsibility</h3>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `renderTemplate throws exception when the specified service template does not exist`() {
      val actual = assertThrows<SubjectAccessRequestException> {
        templateRenderingService.renderServiceDataHtml(
          RenderRequest(
            serviceConfiguration = ServiceConfiguration(
              serviceName = "THIS_IS_MADE_UP",
              label = "",
              url = "",
              enabled = true,
              templateMigrated = false,
              category = ServiceCategory.PRISON,
            ),
          ),
          testServiceTemplateData,
        )
      }

      assertThat(actual.message).startsWith("template resource not found")
      assertThat(actual.errorCode).isEqualTo(ErrorCode.TEMPLATE_RESOURCE_NOT_FOUND)

      verify(templateVersionService, never()).getTemplate(any())
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.TemplateRenderingServiceTestFixture#serviceWithMandatoryTemplates")
    fun `renderTemplate throws expected exception when service template is mandated but not template is found`(
      serviceName: String,
    ) {
      val actual = assertThrows<SubjectAccessRequestException> {
        renderServiceDataHtml(serviceName, testServiceTemplateData)
      }
      assertThat(actual.errorCode).isEqualTo(ErrorCode.TEMPLATE_RESOURCE_NOT_FOUND)

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `should resolve valid Id to username last name`() {
      whenever(userDetailsRepository.findByUsername("742")).thenReturn(
        UserDetail(
          username = "Homer",
          lastName = "Simpson",
        ),
      )

      val actual = renderTestServiceData(TestServiceData(userId = "742"))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Username: </td><td>Simpson</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `should resolve to Id when user not found`() {
      whenever(userDetailsRepository.findByUsername("742")).thenReturn(null)

      val actual = renderTestServiceData(TestServiceData(userId = "742"))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Username: </td><td>742</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "       | 'No Data Held'",
        " ''    | 'No Data Held'",
      ],
      delimiter = '|',
    )
    fun `should use no data held when userId is empty null or`(id: String?) {
      val actual = renderTestServiceData(TestServiceData(userId = id))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Prison: </td><td>No Data Held</td>")

      verify(userDetailsRepository, never()).findByUsername(any())
      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `should resolve valid prison Id to prison name`() {
      whenever(prisonDetailsRepository.findByPrisonId("SPT")).thenReturn(
        PrisonDetail(
          prisonId = "SPT",
          prisonName = "Springfield Penitentiary",
        ),
      )

      val actual = renderTestServiceData(TestServiceData(prisonCode = "SPT"))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Prison: </td><td>Springfield Penitentiary</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @Test
    fun `should use prisonId value when prison Id is not found`() {
      whenever(prisonDetailsRepository.findByPrisonId("SPT")).thenReturn(null)

      val actual = renderTestServiceData(TestServiceData(prisonCode = "SPT"))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Prison: </td><td>SPT</td>")

      verify(templateVersionService, never()).getTemplate(any())
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "       | 'No Data Held'",
        " ''    | 'No Data Held'",
      ],
      delimiter = '|',
    )
    fun `should use no data held when caseload is empty null or`(prisonCode: String?) {
      val actual = renderTestServiceData(TestServiceData(prisonCode = prisonCode))

      assertThat(actual).isNotEmpty()
      assertThat(actual).contains("<td>Prison: </td><td>No Data Held</td>")
      verify(prisonDetailsRepository, never()).findByPrisonId(any())
    }

    private fun renderTestServiceData(data: TestServiceData): String {
      val actual = renderServiceDataHtml("test-service", data)

      assertThat(actual).isNotNull()
      assertThat(actual.templateVersion).isEqualTo(expectedTemplateVersion)
      assertThat(actual.data).isNotNull()

      return actual.outputStreamToString()
    }
  }

  @Nested
  inner class MigratedTemplate {

    private val keyworkerMigratedServiceConfiguration = ServiceConfiguration(
      serviceName = "keyworker-api",
      label = "Keyworker",
      url = "",
      enabled = true,
      templateMigrated = true,
      category = ServiceCategory.PRISON,
    )

    private val testServiceMigratedServiceConfiguration = ServiceConfiguration(
      serviceName = "test-service",
      label = "Test Service",
      url = "",
      enabled = true,
      templateMigrated = true,
      category = ServiceCategory.PRISON,
    )

    @Test
    fun `should correctly render migrated service template`() {
      val renderRequest = RenderRequest(serviceConfiguration = keyworkerMigratedServiceConfiguration)

      whenever(templateVersionService.getTemplate(renderRequest))
        .thenReturn(getTemplateDetails(renderRequest.serviceConfiguration.serviceName))

      val actual = templateRenderingService.renderServiceDataHtml(renderRequest, testKeyworkerServiceData)
      assertThat(actual).isNotNull()

      assertThat(actual).isNotNull()
      assertThat(actual.data).isNotNull()
      assertThat(actual.templateVersion).isEqualTo("1")
      val renderedTemplate = actual.outputStreamToString()

      assertThat(renderedTemplate).contains("<style>")
      assertThat(renderedTemplate).contains("</style>")
      assertThat(renderedTemplate).contains("<td>Allocated at</td><td>03 December 2019, 11:00:58 am</td>")
      assertThat(renderedTemplate).contains("<td>Allocation is active</td><td>No</td>")

      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }

    @Test
    fun `should throw exception when getTemplate throws an error`() {
      val renderRequest = RenderRequest(serviceConfiguration = keyworkerMigratedServiceConfiguration)

      whenever(templateVersionService.getTemplate(renderRequest)).thenThrow(
        SubjectAccessRequestException(
          message = "Boom",
          errorCode = ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH,
          subjectAccessRequestId = renderRequest.id,
        ),
      )

      val actual = assertThrows<SubjectAccessRequestException> {
        templateRenderingService.renderServiceDataHtml(
          renderRequest, testKeyworkerServiceData,
        )
      }

      assertThat(actual).isNotNull
      assertThat(actual.message).startsWith("Boom")
      assertThat(actual.errorCode).isEqualTo(ErrorCode.SERVICE_TEMPLATE_HASH_MISMATCH)
      assertThat(actual.subjectAccessRequestId).isEqualTo(renderRequest.id)

      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }

    @Test
    fun `should resolve valid prisonID to expected prison name`() {
      val renderRequest = RenderRequest(serviceConfiguration = testServiceMigratedServiceConfiguration)

      whenever(templateVersionService.getTemplate(renderRequest))
        .thenReturn(getTemplateDetails(renderRequest.serviceConfiguration.serviceName))

      whenever(prisonDetailsRepository.findByPrisonId("SPT")).thenReturn(
        PrisonDetail(
          prisonId = "SPT",
          prisonName = "Springfield Penitentiary",
        ),
      )

      val actual = templateRenderingService.renderServiceDataHtml(renderRequest, testServiceTemplateData)
      assertThat(actual).isNotNull()

      assertThat(actual.outputStreamToString()).isNotEmpty()
      assertThat(actual.outputStreamToString()).contains("<td>Prison: </td><td>Springfield Penitentiary</td>")
      assertThat(actual.outputStreamToString()).doesNotContain("<td>Prison: </td><td>SPT</td>")
      verify(templateVersionService, times(1)).getTemplate(renderRequest)
    }

    private fun getTemplateDetails(serviceName: String): TemplateDetails {
      val templateBody = this::class.java.getResource("/templates/template_${serviceName}.mustache")?.readText()
      assertThat(templateBody).isNotNull
      assertThat(templateBody).isNotEmpty
      return TemplateDetails(version = "1", body = templateBody!!)
    }
  }

  private data class TestServiceData(
    val testKey: String? = null,
    val prisonCode: String? = null,
    val userId: String? = null,
    val moreData: Map<String, Any> = emptyMap(),
    val arrayData: MutableList<String> = mutableListOf(),
  )


  fun RenderedHtml.outputStreamToString(): String = String(this.data!!.toByteArray())
}
