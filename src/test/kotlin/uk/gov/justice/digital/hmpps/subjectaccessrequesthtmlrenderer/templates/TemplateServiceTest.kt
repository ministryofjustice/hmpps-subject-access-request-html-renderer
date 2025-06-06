package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository

class TemplateServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository, locationDetailsRepository, locationsApiClient, nomisMappingApiClient)
  private val templateService = TemplateService(
    templateHelpers,
    TemplateResources(templatesDirectory = "/templates"),
    telemetryClient,
  )

  private fun renderServiceDataHtml(serviceName: String, data: Any?): String {
    val actual = templateService.renderServiceDataHtml(RenderRequest(serviceName = serviceName), data)
    assertThat(actual).isNotNull()
    return String(actual!!.toByteArray())
  }

  @Test
  fun `renderTemplate renders a style template given a service template`() {
    val actual = renderServiceDataHtml("test-service", testServiceTemplateData)

    assertThat(actual).isNotNull()
    assertThat(actual).isNotEmpty()
    assertThat(actual).contains("<style>")
    assertThat(actual).contains("</style>")
    assertThat(actual).contains("<td>Test Key:</td><td>testValue</td>")
    assertThat(actual).contains("<td>Nested Data:</td><td>nestedValue</td>")
    assertThat(actual).contains("<td>Array Data:</td><td><ul><li>arrayValue1-1</li><li>arrayValue1-2</li></ul></td>")
    assertThat(actual).contains("<td>Test Key:</td><td>testValue2</td>")
    assertThat(actual).contains("<td>Nested Data:</td><td>nestedValue2</td>")
    assertThat(actual).contains("<td>Array Data:</td><td><ul><li>arrayValue2-1</li><li>arrayValue2-2</li></ul></td>")
  }

  @Test
  fun `renderTemplate renders a template given a keyworker template`() {
    val actual = renderServiceDataHtml("keyworker-api", testKeyworkerServiceData)

    assertThat(actual).isNotNull()
    assertThat(actual).contains("<style>")
    assertThat(actual).contains("</style>")
    assertThat(actual).contains("<td>Allocated at</td><td>03 December 2019, 11:00:58 am</td>")
    assertThat(actual).contains("<td>Allocation is active</td><td>No</td>")
  }

  @Test
  fun `renderTemplate renders a template given an activities template`() {
    val actual = renderServiceDataHtml("hmpps-activities-management-api", testActivitiesServiceData)

    assertThat(actual).isNotNull()
    assertThat(actual).contains("<style>")
    assertThat(actual).contains("</style>")
    assertThat(actual).contains("<td class=\"data-column-25\">End date</td>")
    assertThat(actual).contains("<h3>Application - Waiting list ID 1</h3>")
    assertThat(actual).contains("<h3>Application - Waiting list ID 10</h3>")
    assertThat(actual).contains("<td class=\"data-column-25\">Status date</td>")
    assertThat(actual).contains("<h3>Appointment</h3>")
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

    assertThat(actual).contains("<style>")
    assertThat(actual).contains("</style>")
    assertThat(actual).contains("<td>03 December 2019</td>")
    assertThat(actual).contains("<td>03 July 2023, 9:14:25 pm</td>")
  }

  @Test
  fun `renderTemplate renders a template given an adjudications template`() {
    val actual = renderServiceDataHtml("hmpps-manage-adjudications-api", testAdjudicationsServiceData)

    assertThat(actual).isNotNull()
    assertThat(actual).contains("<style>")
    assertThat(actual).contains("</style>")
    assertThat(actual).contains("<td>Date and time of incident</td><td>08 June 2023, 12:00:00 pm</td>")
    assertThat(actual).contains("<td>Description</td><td>Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:</td>")
    assertThat(actual).contains("<td>Description</td><td>Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own</td>")
    assertThat(actual).contains("<td>Status</td><td>CHARGE_PROVED</td>")
    assertThat(actual).contains("<td>ELECTRICAL_REPAIR</td>")
    assertThat(actual).contains("<td>BAGGED_AND_TAGGED</td>")
    assertThat(actual).contains("<td>OIC hearing type</td><td>INAD_ADULT</td>")
    assertThat(actual).contains("<td>James Warburton</td>")
    assertThat(actual).contains("<td>Code</td><td>CHARGE_PROVED</td>")
    assertThat(actual).contains("<td class=\"data-column-30\">Privilege type</td>")
    assertThat(actual).contains("<td>Linked charge numbers</td><td>9872-1, 9872-2</td>")
    assertThat(actual).contains("<td>DAYS</td>")
    assertThat(actual).contains("<td>Some info</td>")
    assertThat(actual).contains("<td>Reason for change</td><td>APPEAL</td>")
  }

  @Test
  fun `renderTemplate renders a template given a home detentions curfew template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-hdc-api", testHDCServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-25\">Vary version</td>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-25\">Has considered checks</td>")
    assertThat(renderedStyleTemplate).contains("<tr><td>First night from</td><td>15:00</td></tr>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-15\">Friday from</td>")
    assertThat(renderedStyleTemplate).contains("<td>Decision maker</td><td>Test User</td>")
    assertThat(renderedStyleTemplate).contains("<td>Offence committed before Feb 2015</td><td>No</td>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-25\">Telephone</td>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-25\">Curfew address line 1</td>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Bass requested</td><td>Yes</td></tr>")
    assertThat(renderedStyleTemplate).contains("<td>Additional conditions required</td><td>No</td>")
  }

  @Test
  fun `renderTemplate renders a template given a Use of Force template`() {
    whenever(userDetailsRepository.findByUsername("ZANDYUSER_ADM")).thenReturn(
      UserDetail(
        "ZANDYUSER_ADM",
        "Lee",
      ),
    )

    val renderedStyleTemplate = renderServiceDataHtml("hmpps-uof-data-api", testUseOfForceServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-50\">Incident date</td>")
    assertThat(renderedStyleTemplate).contains("<td>CCTV recording</td>")
    assertThat(renderedStyleTemplate).contains("<td>Name</td><td>Lee</td>")
    assertThat(renderedStyleTemplate).contains("<td class=\"data-column-25\">Baton drawn</td>")
  }

  @Test
  fun `renderTemplate renders a template given a prepare someone for release template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-resettlement-passport-api", testResettlementPassportServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("Prison name")
    assertThat(renderedStyleTemplate).contains("Deed poll certificate")
    assertThat(renderedStyleTemplate).contains("Account opened")
    assertThat(renderedStyleTemplate).contains("FINANCE_AND_ID")
    assertThat(renderedStyleTemplate).contains("Date application submitted")
    assertThat(renderedStyleTemplate).contains("James Boobier")
    assertThat(renderedStyleTemplate).contains("DRUGS_AND_ALCOHOL")
    assertThat(renderedStyleTemplate).contains("Help finding accomodation")
  }

  @Test
  fun `renderTemplate renders a template given a court case service template`() {
    val renderedStyleTemplate = renderServiceDataHtml("court-case-service", testCourtCaseServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<h5>Notes</h5>")
    assertThat(renderedStyleTemplate).contains("This is a note")
    assertThat(renderedStyleTemplate).contains("<h5>Outcomes</h5>")
    assertThat(renderedStyleTemplate).contains("ADJOURNED")
    assertThat(renderedStyleTemplate).contains("<h3>Comments</h3>")
    assertThat(renderedStyleTemplate).contains("Author One")
  }

  @Test
  fun `renderTemplate renders a template given a accredited programme service template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-accredited-programmes-api", testAccreditedProgrammesServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<h2>Referrals</h2>")
    assertThat(renderedStyleTemplate).contains("<td>Becoming New Me Plus</td>")
    assertThat(renderedStyleTemplate).contains("<td>12 March 2024, 2:23:12 pm</td>")
    assertThat(renderedStyleTemplate).contains("<td>Kaizen</td>")
    assertThat(renderedStyleTemplate).contains("<td>AELANGOVAN_ADM</td>")
  }

  @Test
  fun `renderTemplate renders a template given a Non-associations template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-non-associations-api", testNonAssociationsServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<h3>Non-association - ID 83493</h3>")
    assertThat(renderedStyleTemplate).contains("Restriction type")
    assertThat(renderedStyleTemplate).contains("This is a test for SAR")
  }

  @Test
  fun `renderTemplate renders a template given a Interventions Service template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-interventions-service", testInterventionsServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<h2>Referrals</h2>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Referral number</td><td>JE2862AC</td></tr>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Late reason</td><td>SAR Test 21 - Add how late they were and anything you know about the reason.</td></tr>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Referral number</td><td>FY7705FI</td></tr>")
    assertThat(renderedStyleTemplate).contains("<p>No Data Held</p>")
  }

  @Test
  fun `renderTemplate renders a template given a Categorisation Service template`() {
    val renderedStyleTemplate = renderServiceDataHtml("hmpps-offender-categorisation-api", testCategorisationServiceData)

    assertThat(renderedStyleTemplate).isNotNull()
    assertThat(renderedStyleTemplate).contains("<style>")
    assertThat(renderedStyleTemplate).contains("</style>")
    assertThat(renderedStyleTemplate).contains("<h3>Categorisation form</h3>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Text</td><td>previous terrorism offences text - talking about bombs</td></tr>")
    assertThat(renderedStyleTemplate).contains("<h5>Ratings</h5>")
    assertThat(renderedStyleTemplate).contains("<tr><td>Risk information last updated</td><td>27 July 2021, 2:17:48 am</td></tr>")
  }

  @Test
  fun `renderTemplate throws exception when the specified service template does not exist`() {
    val actual = assertThrows<SubjectAccessRequestTemplatingException> {
      templateService.renderServiceDataHtml(RenderRequest(serviceName = "THIS_IS_MADE_UP"), testServiceTemplateData)
    }

    assertThat(actual.message).startsWith("template resource not found")
  }

  @ParameterizedTest
  @MethodSource("serviceWithMandatoryTemplates")
  fun `renderTemplate throws expected exception when service template is mandated but not template is found`(serviceName: String) {
    assertThrows<SubjectAccessRequestTemplatingException> {
      renderServiceDataHtml(serviceName, testServiceTemplateData)
    }
  }

  companion object {
    @JvmStatic
    fun serviceWithMandatoryTemplates() = listOf("GOne", "GTwo", "GThree")

    private val testServiceTemplateData: ArrayList<Any> = arrayListOf(
      mapOf(
        "testKey" to "testValue",
        "moreData" to mapOf(
          "nestedKey" to "nestedValue",
        ),
        "arrayData" to arrayListOf(
          "arrayValue1-1",
          "arrayValue1-2",
        ),
      ),
      mapOf(
        "testKey" to "testValue2",
        "moreData" to mapOf(
          "nestedKey" to "nestedValue2",
        ),
        "arrayData" to arrayListOf(
          "arrayValue2-1",
          "arrayValue2-2",
        ),
      ),
    )

    private val testKeyworkerServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "allocatedAt" to "2019-12-03T11:00:58.21264",
        "allocationExpiredAt" to "2020-01-23T14:03:23.21264",
        "activeAllocation" to false,
        "allocationReason" to "Manual",
        "allocationType" to "Manual",
        "keyworker" to mapOf(
          "firstName" to "",
          "lastName" to "AUSER_GEN",
        ),
        "prisonCode" to "MDI",
        "deallocationReason" to "Released",
      ),
    )

    private val testActivitiesServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "prisonerNumber" to "A4743DZ",
        "fromDate" to "1970-01-01",
        "toDate" to "2000-01-01",
        "allocations" to arrayListOf(
          mapOf(
            "allocationId" to 16,
            "prisonCode" to "LEI",
            "prisonerStatus" to "ENDED",
            "startDate" to "2023-07-21",
            "endDate" to "2023-07-21",
            "activityId" to 3,
            "activitySummary" to "QAtestingKitchenActivity",
            "payBand" to "Pay band 5",
            "createdDate" to "2023-07-20",
          ),
          mapOf(
            "allocationId" to 16,
            "prisonCode" to "LEI",
            "prisonerStatus" to "ENDED",
            "startDate" to "2023-07-21",
            "endDate" to "2023-07-21",
            "activityId" to 3,
            "activitySummary" to "QAtestingKitchenActivity",
            "payBand" to "Pay band 5",
            "createdDate" to "2023-07-20",
          ),
        ),
        "attendanceSummary" to arrayListOf(
          mapOf(
            "attendanceReasonCode" to "ATTENDED",
            "count" to 12,
          ),
          mapOf(
            "attendanceReasonCode" to "CANCELLED",
            "count" to 8,
          ),
        ),
        "waitingListApplications" to arrayListOf(
          mapOf(
            "waitingListId" to 1,
            "prisonCode" to "LEI",
            "activitySummary" to "Summary",
            "applicationDate" to "2023-08-11",
            "originator" to "Prison staff",
            "status" to "APPROVED",
            "statusDate" to "2022-11-12",
            "comments" to null,
            "createdDate" to "2023-08-10",
          ),
          mapOf(
            "waitingListId" to 10,
            "prisonCode" to "LEI",
            "activitySummary" to "Summary",
            "applicationDate" to "2024-08-11",
            "originator" to "Prison staff",
            "status" to "APPROVED",
            "statusDate" to null,
            "comments" to null,
            "createdDate" to "2023-08-10",
          ),
        ),
        "appointments" to arrayListOf(
          mapOf(
            "appointmentId" to 18305,
            "prisonCode" to "LEI",
            "categoryCode" to "CAREER",
            "startDate" to "2023-08-11",
            "startTime" to "10:00",
            "endTime" to "11:00",
            "extraInformation" to "",
            "attended" to "Unmarked",
            "createdDate" to "2023-08-10",
          ),
          mapOf(
            "appointmentId" to 16340,
            "prisonCode" to "LEI",
            "categoryCode" to "CAREER",
            "startDate" to "2023-08-11",
            "startTime" to "10:00",
            "endTime" to "11:00",
            "extraInformation" to "",
            "attended" to "Unmarked",
            "createdDate" to "2023-08-10",
          ),
        ),
      ),
    )

    private val testIncentivesServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "id" to 2898970,
        "bookingId" to "1208204",
        "prisonerNumber" to "A485634",
        "nextReviewDate" to "2019-12-03",
        "levelCode" to "ENH",
        "prisonId" to "MDI",
        "locationId" to "M-16-15",
        "reviewTime" to "2023-07-03T21:14:25.059172",
        "reviewedBy" to "MDI",
        "commentText" to "comment",
        "current" to true,
        "reviewType" to "REVIEW",
      ),
      mapOf(
        "id" to 2898971,
        "bookingId" to "4028021",
        "prisonerNumber" to "A1234AA",
        "nextReviewDate" to "2020-12-03",
        "levelCode" to "ENH",
        "prisonId" to "MDI",
        "locationId" to "M-16-15",
        "reviewTime" to "2023-07-03T21:14:25.059172",
        "reviewedBy" to "MDI",
        "commentText" to "comment",
        "current" to true,
        "reviewType" to "REVIEW",
      ),
    )

    private val testAdjudicationsServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "chargeNumber" to "1525733",
        "prisonerNumber" to "A3863DZ",
        "gender" to "FEMALE",
        "incidentDetails" to mapOf(
          "locationId" to 26149,
          "dateTimeOfIncident" to "2023-06-08T12:00:00",
          "dateTimeOfDiscovery" to "2023-06-08T12:00:00",
          "handoverDeadline" to "2023-06-10T12:00:00",
        ),
        "isYouthOffender" to false,
        "incidentRole" to mapOf(
          "roleCode" to "25c",
          "offenceRule" to mapOf(
            "paragraphNumber" to "25(c)",
            "paragraphDescription" to "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
          ),
          "associatedPrisonersNumber" to "A3864DZ",
        ),
        "offenceDetails" to mapOf(
          "offenceCode" to 16001,
          "offenceRule" to mapOf(
            "paragraphNumber" to "16",
            "paragraphDescription" to "Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own",
            "nomisCode" to "51:16",
            "withOthersNomisCode" to "51:25C",
          ),
          "protectedCharacteristics" to mapOf(
            "id" to 247,
            "characteristic" to "AGE",
          ),
        ),
        "incidentStatement" to mapOf(
          "statement" to "Vera incited Brian Duckworth to set fire to a lamp\r\ndamages - the lamp\r\nevidence includes something in a bag with a reference number of 1234\r\nwitnessed by amarktest",
          "completed" to true,
        ),
        "createdByUserId" to "LBENNETT_GEN",
        "createdDateTime" to "2023-06-08T14:17:20.831884",
        "status" to "CHARGE_PROVED",
        "reviewedByUserId" to "AMARKE_GEN",
        "statusReason" to "",
        "statusDetails" to "",
        "damages" to arrayListOf(
          mapOf(
            "code" to "ELECTRICAL_REPAIR",
            "details" to "mend a lamp",
            "reporter" to "LBENNETT_GEN",
          ),
        ),
        "evidence" to arrayListOf(
          mapOf(
            "code" to "BAGGED_AND_TAGGED",
            "identifier" to "1234",
            "details" to "evidence in a bag with a reference number",
            "reporter" to "LBENNETT_GEN",
          ),
        ),
        "witnesses" to arrayListOf(
          mapOf(
            "code" to "OFFICER",
            "firstName" to "Andrew",
            "lastName" to "Marke",
            "reporter" to "LBENNETT_GEN",
          ),
        ),
        "hearings" to arrayListOf(
          mapOf(
            "id" to 467,
            "locationId" to 775,
            "dateTimeOfHearing" to "2023-06-08T14:25:00",
            "oicHearingType" to "INAD_ADULT",
            "outcome" to mapOf(
              "id" to 534,
              "adjudicator" to "James Warburton",
              "code" to "COMPLETE",
              "plea" to "GUILTY",
            ),
            "agencyId" to "MDI",
          ),
        ),
        "disIssueHistory" to arrayListOf(
          mapOf(
            "issuingOfficer" to "someone",
            "dateTimeOfIssue" to "2023-06-08T14:25:00",
          ),
        ),
        "dateTimeOfFirstHearing" to "2023-06-08T14:25:00",
        "outcomes" to arrayListOf(
          mapOf(
            "hearing" to mapOf(
              "id" to 467,
              "locationId" to 775,
              "dateTimeOfHearing" to "2023-06-08T14:25:00",
              "oicHearingType" to "INAD_ADULT",
              "outcome" to mapOf(
                "id" to 534,
                "adjudicator" to "James Warburton",
                "code" to "COMPLETE",
                "plea" to "GUILTY",
              ),
              "agencyId" to "MDI",
            ),
            "outcome" to mapOf(
              "outcome" to mapOf(
                "id" to 733,
                "code" to "CHARGE_PROVED",
                "canRemove" to true,
              ),
            ),
          ),
        ),
        "punishments" to arrayListOf(
          mapOf(
            "id" to 241,
            "type" to "PRIVILEGE",
            "privilegeType" to "TV",
            "schedule" to mapOf(
              "days" to 7,
              "duration" to 7,
              "measurement" to "DAYS",
              "startDate" to "2023-06-09",
              "endDate" to "2023-06-16",
            ),
            "canRemove" to true,
            "canEdit" to true,
            "rehabilitativeActivities" to arrayListOf(
              mapOf(
                "id" to 241,
                "details" to "Some info",
                "monitor" to "yes",
                "endDate" to "2023-06-09",
                "totalSessions" to 16,
                "completed" to true,
              ),
            ),
          ),
          mapOf(
            "id" to 240,
            "type" to "DAMAGES_OWED",
            "schedule" to mapOf(
              "days" to 0,
              "duration" to 0,
              "measurement" to "DAYS",
            ),
            "damagesOwedAmount" to 20,
            "canRemove" to true,
            "canEdit" to true,
            "rehabilitativeActivities" to emptyList<Any>(),
          ),
        ),
        "punishmentComments" to mapOf(
          "id" to 1,
          "comment" to "test comment",
          "reasonForChange" to "APPEAL",
          "nomisCreatedBy" to "person",
          "actualCreatedDate" to "2023-06-16",
        ),
        "outcomeEnteredInNomis" to false,
        "originatingAgencyId" to "MDI",
        "linkedChargeNumbers" to arrayListOf("9872-1", "9872-2"),
        "canActionFromHistory" to false,
      ),
    )

    private val testHDCServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "licences" to arrayListOf(
          mapOf(
            "id" to 1626,
            "prisonNumber" to "G1556UH",
            "bookingId" to 1108337,
            "stage" to "PROCESSING_RO",
            "version" to 1,
            "transitionDate" to "2024-03-18T09:24:35.473079",
            "varyVersion" to 0,
            "additionalConditionsVersion" to null,
            "standardConditionsVersion" to null,
            "deletedAt" to "2024-03-18T09:25:06.780003",
            "licence" to mapOf(
              "eligibility" to mapOf(
                "crdTime" to mapOf(
                  "decision" to "No",
                ),
                "excluded" to mapOf(
                  "decision" to "No",
                ),
                "suitability" to mapOf(
                  "decision" to "No",
                ),
              ),
              "bassReferral" to mapOf(
                "bassRequest" to mapOf(
                  "bassRequested" to "Yes",
                ),
              ),
              "proposedAddress" to mapOf(
                "curfewAddress" to mapOf(
                  "addressLine1" to "Test Street",
                  "addressLine2" to "",
                  "addressTown" to "Test Town",
                  "postCode" to "B1 2TJ",
                ),
              ),
              "curfew" to mapOf(
                "firstNight" to mapOf(
                  "firstNightFrom" to "15:00",
                  "firstNightUntil" to "07:00",
                ),
              ),
              "curfewHours" to mapOf(
                "allFrom" to "19:00",
                "allUntil" to "07:00",
                "fridayFrom" to "19:00",
                "mondayFrom" to "19:00",
                "sundayFrom" to "19:00",
                "fridayUntil" to "07:00",
                "mondayUntil" to "07:00",
                "sundayUntil" to "07:00",
                "tuesdayFrom" to "19:00",
                "saturdayFrom" to "19:00",
                "thursdayFrom" to "19:00",
                "tuesdayUntil" to "07:00",
                "saturdayUntil" to "07:00",
                "thursdayUntil" to "07:00",
                "wednesdayFrom" to "19:00",
                "wednesdayUntil" to "07:00",
                "daySpecificInputs" to "No",
              ),
              "risk" to mapOf(
                "riskManagement" to mapOf(
                  "version" to "3",
                  "emsInformation" to "No",
                  "pomConsultation" to "Yes",
                  "mentalHealthPlan" to "No",
                  "unsuitableReason" to "",
                  "hasConsideredChecks" to "Yes",
                  "manageInTheCommunity" to "Yes",
                  "emsInformationDetails" to "",
                  "riskManagementDetails" to "",
                  "proposedAddressSuitable" to "Yes",
                  "awaitingOtherInformation" to "No",
                  "nonDisclosableInformation" to "No",
                  "nonDisclosableInformationDetails" to "",
                  "manageInTheCommunityNotPossibleReason" to "",
                ),
              ),
              "reporting" to mapOf(
                "reportingInstructions" to mapOf(
                  "name" to "sam",
                  "postcode" to "S3 8RD",
                  "telephone" to "47450",
                  "townOrCity" to "Sheffield",
                  "organisation" to "crc",
                  "reportingDate" to "12/12/2024",
                  "reportingTime" to "12:12",
                  "buildingAndStreet1" to "10",
                  "buildingAndStreet2" to "street",
                ),
              ),
              "victim" to mapOf(
                "victimLiaison" to mapOf(
                  "decision" to "No",
                ),
              ),
              "licenceConditions" to mapOf(
                "standard" to mapOf(
                  "additionalConditionsRequired" to "No",
                ),
              ),
              "document" to mapOf(
                "template" to mapOf(
                  "decision" to "hdc_ap",
                  "offenceCommittedBeforeFeb2015" to "No",
                ),
              ),
              "approval" to mapOf(
                "release" to mapOf(
                  "decision" to "Yes",
                  "decisionMaker" to "Test User",
                  "reasonForDecision" to "",
                ),
              ),
              "finalChecks" to mapOf(
                "onRemand" to mapOf(
                  "decision" to "No",
                ),
                "seriousOffence" to mapOf(
                  "decision" to "No",
                ),
                "confiscationOrder" to mapOf(
                  "decision" to "No",
                ),
              ),
            ),
          ),
        ),
      ),
      mapOf(
        "licenceVersions" to arrayListOf(
          mapOf(
            "id" to 446,
            "prisonNumber" to "G1556UH",
            "bookingId" to 1108337,
            "timestamp" to "2024-03-15T10:48:50.888663",
            "version" to 1,
            "template" to "hdc_ap",
            "varyVersion" to 0,
            "deletedAt" to "2024-03-15T11:11:14.361319",
            "licence" to mapOf(
              "risk" to mapOf(
                "riskManagement" to mapOf(
                  "version" to "3",
                  "emsInformation" to "No",
                  "pomConsultation" to "Yes",
                  "mentalHealthPlan" to "No",
                  "unsuitableReason" to "",
                  "hasConsideredChecks" to "Yes",
                  "manageInTheCommunity" to "Yes",
                  "emsInformationDetails" to "",
                  "riskManagementDetails" to "",
                  "proposedAddressSuitable" to "Yes",
                  "awaitingOtherInformation" to "No",
                  "nonDisclosableInformation" to "No",
                  "nonDisclosableInformationDetails" to "",
                  "manageInTheCommunityNotPossibleReason" to "",
                ),
              ),
              "curfew" to mapOf(
                "firstNight" to mapOf(
                  "firstNightFrom" to "15:00",
                  "firstNightUntil" to "07:00",
                ),
              ),
              "curfewHours" to mapOf(
                "allFrom" to "19:00",
                "allUntil" to "07:00",
                "fridayFrom" to "19:00",
                "mondayFrom" to "19:00",
                "sundayFrom" to "19:00",
                "fridayUntil" to "07:00",
                "mondayUntil" to "07:00",
                "sundayUntil" to "07:00",
                "tuesdayFrom" to "19:00",
                "saturdayFrom" to "19:00",
                "thursdayFrom" to "19:00",
                "tuesdayUntil" to "07:00",
                "saturdayUntil" to "07:00",
                "thursdayUntil" to "07:00",
                "wednesdayFrom" to "19:00",
                "wednesdayUntil" to "07:00",
                "daySpecificInputs" to "No",
              ),
              "victim" to mapOf(
                "victimLiaison" to mapOf(
                  "decision" to "No",
                ),
              ),
              "approval" to mapOf(
                "release" to mapOf(
                  "decision" to "Yes",
                  "decisionMaker" to "Louise Norris",
                  "reasonForDecision" to "",
                ),
              ),
              "consideration" to mapOf(
                "decision" to "Yes",
              ),
              "document" to mapOf(
                "template" to mapOf(
                  "decision" to "hdc_ap",
                  "offenceCommittedBeforeFeb2015" to "No",
                ),
              ),
              "reporting" to mapOf(
                "reportingInstructions" to mapOf(
                  "name" to "sam",
                  "postcode" to "S3 8RD",
                  "telephone" to "47450",
                  "townOrCity" to "Sheffield",
                  "organisation" to "crc",
                  "reportingDate" to "12/12/2024",
                  "reportingTime" to "12:12",
                  "buildingAndStreet1" to "10",
                  "buildingAndStreet2" to "street",
                ),
              ),
              "eligibility" to mapOf(
                "crdTime" to mapOf(
                  "decision" to "No",
                ),
                "excluded" to mapOf(
                  "decision" to "No",
                ),
                "suitability" to mapOf(
                  "decision" to "No",
                ),
              ),
              "finalChecks" to mapOf(
                "onRemand" to mapOf(
                  "decision" to "No",
                ),
                "segregation" to mapOf(
                  "decision" to "No",
                ),
                "seriousOffence" to mapOf(
                  "decision" to "No",
                ),
                "confiscationOrder" to mapOf(
                  "decision" to "No",
                ),
                "undulyLenientSentence" to mapOf(
                  "decision" to "No",
                ),
              ),
              "bassReferral" to mapOf(
                "bassOffer" to mapOf(
                  "bassArea" to "Reading",
                  "postCode" to "RG1 6HM",
                  "telephone" to "",
                  "addressTown" to "Reading",
                  "addressLine1" to "The Street",
                  "addressLine2" to "",
                  "bassAccepted" to "Yes",
                  "bassOfferDetails" to "",
                ),
                "bassRequest" to mapOf(
                  "specificArea" to "No",
                  "bassRequested" to "Yes",
                  "additionalInformation" to "",
                ),
                "bassAreaCheck" to mapOf(
                  "bassAreaReason" to "",
                  "bassAreaCheckSeen" to "true",
                  "approvedPremisesRequiredYesNo" to "No",
                ),
              ),
              "proposedAddress" to mapOf(
                "optOut" to mapOf(
                  "decision" to "No",
                ),
                "addressProposed" to mapOf(
                  "decision" to "No",
                ),
              ),
              "licenceConditions" to mapOf(
                "standard" to mapOf(
                  "additionalConditionsRequired" to "No",
                ),
              ),
            ),
          ),
        ),
      ),
      mapOf(
        "auditEvents" to arrayListOf(
          mapOf(
            "id" to 40060,
            "timestamp" to "2024-08-23T09:36:51.186289",
            "user" to "cpxUKKZdbW",
            "action" to "UPDATE_SECTION",
            "details" to mapOf(
              "path" to "/hdc/curfew/approvedPremises/1108337",
              "bookingId" to "1108337",
              "userInput" to mapOf(
                "required" to "Yes",
              ),
            ),
          ),
        ),
      ),
    )

    private val testUseOfForceServiceData: ArrayList<Any> = arrayListOf(
      mapOf(
        "id" to 190,
        "sequenceNo" to 2,
        "createdDate" to "2020-09-04T12:12:53.812536",
        "updatedDate" to "2021-03-30T11:31:16.854361",
        "incidentDate" to "2020-09-07T02:02:00",
        "submittedDate" to "2021-03-30T11:31:16.853",
        "deleted" to "2021-11-30T15:47:13.139",
        "status" to "SUBMITTED",
        "agencyId" to "MDI",
        "userId" to "ANDYUSER_ADM",
        "reporterName" to "Andrew User",
        "offenderNo" to "A1234AA",
        "bookingId" to 1048991,
        "formResponse" to mapOf(
          "evidence" to mapOf(
            "cctvRecording" to "YES",
            "baggedEvidence" to true,
            "bodyWornCamera" to "YES",
            "photographsTaken" to false,
            "evidenceTagAndDescription" to arrayListOf(
              mapOf(
                "description" to "sasasasas",
                "evidenceTagReference" to "sasa",
              ),
              mapOf(
                "description" to "sasasasas 2",
                "evidenceTagReference" to "sasa 2",
              ),
            ),
            "bodyWornCameraNumbers" to arrayListOf(
              mapOf(
                "cameraNum" to "sdsds",
              ),
              mapOf(
                "cameraNum" to "xxxxx",
              ),
            ),
          ),
          "involvedStaff" to arrayListOf(
            mapOf(
              "name" to "Andrew User",
              "email" to "andrew.userser@digital.justice.gov.uk",
              "staffId" to 486084,
              "username" to "ZANDYUSER_ADM",
              "verified" to true,
              "activeCaseLoadId" to "MDI",
            ),
            mapOf(
              "name" to "Lee User",
              "email" to "lee.user@digital.justice.gov.uk",
              "staffId" to 486084,
              "username" to "LEEUSER_ADM",
              "verified" to true,
              "activeCaseLoadId" to "MDI",
            ),
          ),
          "incidentDetails" to mapOf(
            "locationId" to 357591,
            "plannedUseOfForce" to false,
            "authorisedBy" to "",
            "witnesses" to arrayListOf(
              mapOf(
                "name" to "Andrew User",
              ),
              mapOf(
                "name" to "Andrew Userdsd",
              ),
            ),
          ),
          "useOfForceDetails" to mapOf(
            "bodyWornCamera" to "YES",
            "bodyWornCameraNumbers" to arrayListOf(
              mapOf(
                "cameraNum" to "sdsds",
              ),
              mapOf(
                "cameraNum" to "sdsds 2",
              ),
            ),
            "pavaDrawn" to false,
            "pavaDrawnAgainstPrisoner" to false,
            "pavaUsed" to false,
            "weaponsObserved" to "YES",
            "weaponTypes" to arrayListOf(
              mapOf(
                "weaponType" to "xxx",
              ),
              mapOf(
                "weaponType" to "yyy",
              ),
            ),
            "escortingHold" to false,
            "restraint" to true,
            "restraintPositions" to arrayListOf(
              "ON_BACK",
              "ON_FRONT",
            ),
            "batonDrawn" to false,
            "batonDrawnAgainstPrisoner" to false,
            "batonUsed" to false,
            "guidingHold" to false,
            "handcuffsApplied" to false,
            "positiveCommunication" to false,
            "painInducingTechniques" to false,
            "painInducingTechniquesUsed" to "NONE",
            "personalProtectionTechniques" to true,
          ),
          "reasonsForUseOfForce" to mapOf(
            "reasons" to arrayListOf(
              "FIGHT_BETWEEN_PRISONERS",
              "REFUSAL_TO_LOCATE_TO_CELL",
            ),
            "primaryReason" to "REFUSAL_TO_LOCATE_TO_CELL",
          ),
          "relocationAndInjuries" to mapOf(
            "relocationType" to "OTHER",
            "f213CompletedBy" to "adcdas",
            "prisonerInjuries" to false,
            "healthcareInvolved" to true,
            "healthcarePractionerName" to "dsffds",
            "prisonerRelocation" to "CELLULAR_VEHICLE",
            "relocationCompliancy" to false,
            "staffMedicalAttention" to true,
            "staffNeedingMedicalAttention" to arrayListOf(
              mapOf(
                "name" to "fdsfsdfs",
                "hospitalisation" to false,
              ),
              mapOf(
                "name" to "fdsfsdfs",
                "hospitalisation" to false,
              ),
            ),
            "prisonerHospitalisation" to false,
            "userSpecifiedRelocationType" to "fsf FGSDgf s gfsdgGG  gf ggrf",
          ),
        ),
        "statements" to arrayListOf(
          mapOf(
            "id" to 334,
            "reportId" to 280,
            "createdDate" to "2021-04-08T09:23:51.165439",
            "updatedDate" to "2021-04-21T10:09:25.626246",
            "submittedDate" to "2021-04-21T10:09:25.626246",
            "deleted" to "2021-04-21T10:09:25.626246",
            "nextReminderDate" to "2021-04-09T09:23:51.165",
            "overdueDate" to "2021-04-11T09:23:51.165",
            "removalRequestedDate" to "2021-04-21T10:09:25.626246",
            "userId" to "ZANDYUSER_ADM",
            "name" to "Andrew User",
            "email" to "andrew.userser@digital.justice.gov.uk",
            "statementStatus" to "REMOVAL_REQUESTED",
            "lastTrainingMonth" to 1,
            "lastTrainingYear" to 2019,
            "jobStartYear" to 2019,
            "staffId" to 486084,
            "inProgress" to true,
            "removalRequestedReason" to "example",
            "statement" to "example",
            "statementAmendments" to arrayListOf(
              mapOf(
                "id" to 334,
                "statementId" to 198,
                "additionalComment" to "this is an additional comment",
                "dateSubmitted" to "2020-10-01T13:08:37.25919",
                "deleted" to "2022-10-01T13:08:37.25919",
              ),
              mapOf(
                "id" to 335,
                "statementId" to 199,
                "additionalComment" to "this is an additional additional comment",
                "dateSubmitted" to "2020-10-01T13:08:37.25919",
                "deleted" to "2022-10-01T13:08:37.25919",
              ),
            ),
          ),
          mapOf(
            "id" to 334,
            "reportId" to 280,
            "createdDate" to "2021-04-08T09:23:51.165439",
            "updatedDate" to "2021-04-21T10:09:25.626246",
            "submittedDate" to "2021-04-21T10:09:25.626246",
            "deleted" to "2021-04-21T10:09:25.626246",
            "nextReminderDate" to "2021-04-09T09:23:51.165",
            "overdueDate" to "2021-04-11T09:23:51.165",
            "removalRequestedDate" to "2021-04-21T10:09:25.626246",
            "userId" to "ZANDYUSER_ADM",
            "name" to "Andrew User",
            "email" to "andrew.userser@digital.justice.gov.uk",
            "statementStatus" to "REMOVAL_REQUESTED",
            "lastTrainingMonth" to 1,
            "lastTrainingYear" to 2019,
            "jobStartYear" to 2019,
            "staffId" to 486084,
            "inProgress" to true,
            "removalRequestedReason" to "example",
            "statement" to "example",
            "statementAmendments" to arrayListOf(
              mapOf(
                "id" to 334,
                "statementId" to 198,
                "additionalComment" to "this is an additional comment",
                "dateSubmitted" to "2020-10-01T13:08:37.25919",
                "deleted" to "2022-10-01T13:08:37.25919",
              ),
              mapOf(
                "id" to 335,
                "statementId" to 199,
                "additionalComment" to "this is an additional additional comment",
                "dateSubmitted" to "2020-10-01T13:08:37.25919",
                "deleted" to "2022-10-01T13:08:37.25919",
              ),
            ),
          ),
        ),
      ),
    )

    private val testResettlementPassportServiceData: Map<Any, Any> = mapOf(
      "prisoner" to mapOf(
        "id" to 3,
        "nomsId" to "A8731DY",
        "creationDate" to "2023-11-17T14:49:58.308566",
        "crn" to "U328968",
        "prisonId" to "MDI",
        "releaseDate" to "2024-09-17",
      ),
      "assessments" to arrayListOf(
        mapOf(
          "id" to 518,
          "prisonerId" to 3,
          "creationDate" to "2024-03-19T15:32:57.283459",
          "assessmentDate" to "2023-01-08T00:00:00",
          "isBankAccountRequired" to false,
          "isIdRequired" to true,
          "isDeleted" to false,
          "deletionDate" to null,
          "idDocuments" to arrayListOf(
            mapOf(
              "id" to 8,
              "name" to "Deed poll certificate",
            ),
            mapOf(
              "id" to 2,
              "name" to "Marriage certificate",
            ),
          ),
        ),
      ),
      "bankApplications" to arrayListOf(
        mapOf(
          "id" to 1537,
          "applicationSubmittedDate" to "2023-12-01T00:00:00",
          "currentStatus" to "Account opened",
          "bankName" to "Co-op",
          "bankResponseDate" to "2023-12-12T00:00:00",
          "isAddedToPersonalItems" to true,
          "addedToPersonalItemsDate" to "2023-12-12T00:00:00",
          "prisoner" to mapOf(
            "id" to 3,
            "nomsId" to "A8731DY",
            "creationDate" to "2023-11-17T14:49:58.308566",
            "crn" to "U328968",
            "prisonId" to "MDI",
            "releaseDate" to "2024-09-17",
          ),
          "logs" to arrayListOf(
            mapOf(
              "id" to 3302,
              "status" to "Pending",
              "changeDate" to "2023-12-01T00:00:00",
            ),
            mapOf(
              "id" to 3303,
              "status" to "Account opened",
              "changeDate" to "2023-12-04T00:00:00",
            ),
          ),
        ),
      ),
      "deliusContacts" to arrayListOf(
        mapOf(
          "caseNoteId" to "db-2",
          "pathway" to "FINANCE_AND_ID",
          "creationDateTime" to "2023-12-13T12:33:30.514175",
          "occurenceDateTime" to "2023-12-13T12:33:30.514175",
          "createdBy" to "James Boobier",
          "text" to "Resettlement status set to: Support not required. This is a case note from Delius",
        ),
        mapOf(
          "caseNoteId" to "db-3",
          "pathway" to "FINANCE_AND_ID",
          "creationDateTime" to "2023-12-13T12:33:30.514175",
          "occurenceDateTime" to "2023-12-13T12:33:30.514175",
          "createdBy" to "James Boobier",
          "text" to "Resettlement status set to: Done. This is a case note from Delius",
        ),
      ),
      "idApplications" to arrayListOf(
        mapOf(
          "idType" to mapOf(
            "id" to 6,
            "name" to "Driving licence",
          ),
          "creationDate" to "2024-05-01T11:12:32.681477",
          "applicationSubmittedDate" to "2024-05-01T00:00:00",
          "isPriorityApplication" to false,
          "costOfApplication" to 100,
          "refundAmount" to 100,
          "haveGro" to null,
          "isUkNationalBornOverseas" to null,
          "countryBornIn" to null,
          "caseNumber" to null,
          "courtDetails" to null,
          "driversLicenceType" to "Renewal",
          "driversLicenceApplicationMadeAt" to "Online",
          "isAddedToPersonalItems" to null,
          "addedToPersonalItemsDate" to null,
          "status" to "Rejected",
          "statusUpdateDate" to "2024-05-01T12:43:56.722624",
          "isDeleted" to false,
          "deletionDate" to null,
          "dateIdReceived" to null,
          "id" to 2148,
          "prisonerId" to 3,
        ),
      ),
      "statusSummary" to arrayListOf(
        mapOf(
          "type" to "BCST2",
          "pathwayStatus" to arrayListOf(
            mapOf(
              "pathway" to "ACCOMMODATION",
              "assessmentStatus" to "SUBMITTED",
            ),
            mapOf(
              "pathway" to "DRUGS_AND_ALCOHOL",
              "assessmentStatus" to "SUBMITTED",
            ),
          ),
        ),
      ),
      "resettlementAssessments" to arrayListOf(
        mapOf(
          "assessmentType" to "BCST2",
          "lastUpdated" to "2024-09-02T08:54:37.979749",
          "updatedBy" to "Nick Judge",
          "questionsAndAnswers" to arrayListOf(
            mapOf(
              "questionTitle" to "Where did the person in prison live before custody?",
              "answer" to "No answer provided",
              "originalPageId" to "ACCOMMODATION_REPORT",
            ),
            mapOf(
              "questionTitle" to "Support needs?",
              "answer" to "Help finding accomodation",
              "originalPageId" to "SUPPORT_REQUIREMENTS",
            ),
          ),
        ),
      ),
    )

    private val testCourtCaseServiceData = mapOf(
      "cases" to arrayListOf(
        mapOf(
          "caseId" to "b4a23007-beb0-4c3f-893f-3a7c80c3521c",
          "hearings" to arrayListOf(
            mapOf(
              "hearingId" to "605e08b9-8544-417e-84fa-39ce337ab04e",
              "notes" to arrayListOf(
                mapOf(
                  "note" to "This is a note",
                  "authorSurname" to "Test Author",
                ),
                mapOf(
                  "note" to "This is a note",
                  "authorSurname" to "Test Author",
                ),
              ),
              "outcomes" to arrayListOf(
                mapOf(
                  "outcomeType" to "OTHER",
                  "outcomeDate" to "2023-06-22T14:12:31.396105",
                  "resultedDate" to "2023-09-12T15:30:13.558769",
                  "state" to "RESULTED",
                  "assignedTo" to "User",
                  "createdDate" to "2023-06-22T14:12:31.428778",
                ),
                mapOf(
                  "outcomeType" to "ADJOURNED",
                  "outcomeDate" to "2023-06-22T14:12:31.396105",
                  "resultedDate" to "2023-09-12T15:30:13.558769",
                  "state" to "RESULTED",
                  "assignedTo" to "User Two",
                  "createdDate" to "2023-06-22T14:12:31.428778",
                ),
              ),
            ),
          ),
          "comments" to arrayListOf(
            mapOf(
              "comment" to "test",
              "authorSurname" to "Author One",
              "created" to "2023-06-21T12:11:21.355792",
              "createdBy" to "USER(prepare-a-case-for-court-1)",
              "lastUpdated" to "2023-06-21T12:11:21.355792",
              "lastUpdatedBy" to "USER(prepare-a-case-for-court-1)",
              "caseNumber" to "2106223516243653402",
            ),
            mapOf(
              "comment" to "Defendant details\\r\\nName\\tJohn Marston\\r\\nGender\\tMale\\r\\nDate of birth\\t28 February 1997 (25 years old)\\r\\nPhone number\\tUnavailable\\r\\nAddress\\t14 Tottenham Court Road\\r\\nLondon Road\\r\\nEngland\\r\\nUK\\r\\nEarth\\r\\nW1T 7RJ\\r\\nComments\\r\\nAdd notes and observations about this case. Your colleagues who use Prepare a Case will be able to read them.\\r\\n\\r\\nThese comments will not be saved to NDelius.\\r\\n\\r\\n",
              "authorSurname" to "Author One",
              "created" to "2023-06-21T12:11:21.355792",
              "createdBy" to "USER(prepare-a-case-for-court-1)",
              "lastUpdated" to "2023-06-21T12:11:21.355792",
              "lastUpdatedBy" to "USER(prepare-a-case-for-court-1)",
              "caseNumber" to "2106223516243653402",
            ),
          ),
        ),
      ),
    )

    private val testAccreditedProgrammesServiceData: Map<Any, Any> = mapOf(
      "referrals" to arrayListOf(
        mapOf(
          "prisonerNumber" to "A8610DY",
          "oasysConfirmed" to true,
          "statusCode" to "DESELECTED",
          "hasReviewedProgrammeHistory" to true,
          "additionalInformation" to "test",
          "submittedOn" to "2024-03-12T14:23:12.328775",
          "referrerUsername" to "AELANGOVAN_ADM",
          "courseName" to "Becoming New Me Plus",
          "audience" to "Sexual offence",
          "courseOrganisation" to "WTI",
        ),
        mapOf(
          "prisonerNumber" to "A8610DY",
          "oasysConfirmed" to false,
          "statusCode" to "REFERRAL_STARTED",
          "hasReviewedProgrammeHistory" to false,
          "additionalInformation" to null,
          "submittedOn" to null,
          "referrerUsername" to "SMCALLISTER_GEN",
          "courseName" to "Becoming New Me Plus",
          "audience" to "Intimate partner violence offence",
          "courseOrganisation" to "AYI",
        ),
      ),
      "courseParticipation" to arrayListOf(
        mapOf(
          "prisonerNumber" to "A8610DY",
          "yearStarted" to null,
          "source" to null,
          "type" to "CUSTODY",
          "outcomeStatus" to "COMPLETE",
          "yearCompleted" to 2020,
          "location" to null,
          "detail" to null,
          "courseName" to "Kaizen",
          "createdByUser" to "ACOOMER_GEN",
          "createdDateTime" to "2024-07-12T14:57:42.431163",
          "updatedByUser" to "ACOOMER_GEN",
          "updatedDateTime" to "2024-07-12T14:58:38.597915",
        ),
        mapOf(
          "prisonerNumber" to "A8610DY",
          "yearStarted" to 2002,
          "source" to "Example",
          "type" to "COMMUNITY",
          "outcomeStatus" to "COMPLETE",
          "yearCompleted" to 2004,
          "location" to "Example",
          "detail" to "Example",
          "courseName" to "Enhanced Thinking Skills",
          "createdByUser" to "AELANGOVAN_ADM",
          "createdDateTime" to "2024-07-12T14:57:42.431163",
          "updatedByUser" to "AELANGOVAN_ADM",
          "updatedDateTime" to "2024-07-12T14:58:38.597915",
        ),
      ),
    )

    private val testNonAssociationsServiceData = mapOf(
      "prisonerNumber" to "A4743DZ",
      "firstName" to "SOLOMON",
      "lastName" to "ANTHONY",
      "prisonId" to "LEI",
      "prisonName" to "Leeds (HMP)",
      "cellLocation" to "RECP",
      "openCount" to 1,
      "closedCount" to 0,
      "nonAssociations" to arrayListOf(
        mapOf(
          "id" to 83493,
          "role" to "PERPETRATOR",
          "roleDescription" to "Perpetrator",
          "reason" to "ORGANISED_CRIME",
          "reasonDescription" to "Organised crime",
          "restrictionType" to "LANDING",
          "restrictionTypeDescription" to "Cell and landing",
          "comment" to "This is a test for SAR",
          "authorisedBy" to "MWILLIS_GEN",
          "whenCreated" to "2024-05-07T14:49:51",
          "whenUpdated" to "2024-05-07T14:49:51",
          "updatedBy" to "MWILLIS_GEN",
          "isClosed" to false,
          "closedBy" to null,
          "closedReason" to null,
          "closedAt" to null,
          "otherPrisonerDetails" to mapOf(
            "prisonerNumber" to "G4769GD",
            "role" to "PERPETRATOR",
            "roleDescription" to "Perpetrator",
            "firstName" to "UDFSANAYE",
            "lastName" to "AARELL",
            "prisonId" to "PRI",
            "prisonName" to "Parc (HMP)",
            "cellLocation" to "T-5-41",
          ),
        ),
      ),
    )

    private val testInterventionsServiceData: Map<Any, Any> = mapOf(
      "crn" to "X718253",
      "referral" to arrayListOf(
        mapOf(
          "referral_number" to "JE2862AC",
          "accessibility_needs" to "SAR test 9 - Does Sadie have any other mobility, disability or accessibility needs? (optional)\r\n - None",
          "additional_needs_information" to "SAR test 10 - Additional information about Sadie’s needs (optional) - None",
          "when_unavailable" to "SAR test 11 - Provide details of when Sadie will not be able to attend sessions - Weekday mornings",
          "end_requested_comments" to "",
          "appointment" to arrayListOf(
            mapOf(
              "session_summary" to "SAR Test 22 - What did you do in the session?",
              "session_response" to "SAR Test 23 - How did Sadie Borer respond to the session?",
              "session_concerns" to "SAR Test 25 - Yes, something concerned me about Sadie Borer",
              "late_reason" to "SAR Test 21 - Add how late they were and anything you know about the reason.",
              "future_session_plan" to "SAR Test 26 - Add anything you have planned for the next session (optional)",
            ),
            mapOf(
              "session_summary" to "SAR 27 - What did you do in the session?",
              "session_response" to "SAR 28 - How did Sadie Borer respond to the session?",
              "session_concerns" to "SAR 30 - Yes, something concerned me about Sadie Borer",
              "late_reason" to "",
              "future_session_plan" to "SAR 31 - Add anything you have planned for the next session (optional)",
            ),
          ),
          "action_plan_activity" to arrayListOf(
            mapOf(
              "description" to arrayListOf(
                "SAR Test 19 - Please write the details of the activity here.",
                "SAR Test 20 - Activity 2 - Please write the details of the activity here.",
              ),
            ),
            mapOf(
              "description" to arrayListOf(
                "example",
              ),
            ),
          ),
          "end_of_service_report" to mapOf(
            "end_of_service_outcomes" to arrayListOf(
              mapOf(
                "progression_comments" to "SAR Test 32 - Describe their progress on this outcome.",
                "additional_task_comments" to "SAR Test 33 - Enter if anything else needs to be done (optional)",
              ),
              mapOf(
                "progression_comments" to "test.",
                "additional_task_comments" to "test",
              ),
            ),
          ),
        ),
        mapOf(
          "referral_number" to "FY7705FI",
          "accessibility_needs" to "mobility",
          "additional_needs_information" to "",
          "when_unavailable" to "Fridays",
          "end_requested_comments" to "",
          "appointment" to emptyList<Any>(),
          "action_plan_activity" to emptyList<Any>(),
          "end_of_service_report" to null,
        ),
      ),
    )

    private val testCategorisationServiceData: Map<Any, Any> = mapOf(
      "catForm" to arrayListOf(
        mapOf(
          "form_response" to mapOf(
            "ratings" to mapOf(
              "escapeRating" to mapOf(
                "escapeCatB" to "Yes",
                "escapeCatBText" to "escape cat b text",
                "escapeOtherEvidence" to "Yes",
                "escapeOtherEvidenceText" to "escape other evidence text",
              ),
              "extremismRating" to mapOf(
                "previousTerrorismOffences" to "Yes",
                "previousTerrorismOffencesText" to "previous terrorism offences text - talking about bombs",
              ),
              "furtherCharges" to mapOf(
                "furtherCharges" to "Yes",
                "furtherChargesCatB" to "Yes",
                "furtherChargesText" to "further charges text",
              ),
              "violenceRating" to mapOf(
                "seriousThreat" to "Yes",
                "seriousThreatText" to "serious threat text",
                "highRiskOfViolence" to "Yes",
                "highRiskOfViolenceText" to "high risk of violence text",
              ),
              "offendingHistory" to mapOf(
                "previousConvictions" to "No",
              ),
              "securityInput" to mapOf(
                "securityInputNeeded" to "Yes",
                "securityInputNeededText" to "Test",
              ),
              "securityBack" to mapOf(
                "catB" to "Yes",
              ),
              "decision" to mapOf(
                "category" to "Test",
              ),
            ),
          ),
          // not included - system ID:
          "booking_id" to "832899",
          "status" to "STARTED",
          "referred_date" to "30-12-2020",
          // not included - system ID:
          "sequence_no" to "1",
          "risk_profile" to mapOf(
            "lifeProfile" to mapOf(
              "life" to true,
              // not included - duplicate ID:
              "nomsId" to "example",
              "riskType" to "example",
              "provisionalCategorisation" to "example",
            ),
            "escapeProfile" to mapOf(
              // not included - duplicate ID:
              "nomsId" to "example",
              "riskType" to "example",
              "provisionalCategorisation" to "example",
            ),
            "violenceProfile" to mapOf(
              // not included - duplicate ID:
              "nomsId" to "example",
              "riskType" to "example",
              "displayAssaults" to true,
              "numberOfAssaults" to 2,
              "notifySafetyCustodyLead" to true,
              "numberOfSeriousAssaults" to 1,
              "numberOfNonSeriousAssaults" to 1,
              "veryHighRiskViolentOffender" to true,
              "provisionalCategorisation" to "example",
            ),
          ),
          "prison_id" to "MDI",
          // not included - duplicate ID:
          "offender_no" to "G2515UU",
          "start_date" to "2024-05-22 10:45:22.627786+01",
          "cat_type" to "INITIAL",
          "review_reason" to "MANUAL",
          "due_by_date" to "2014-06-16",
          "cancelled_date" to "exampleDate",
        ),
      ),
      "liteCategory" to mapOf(
        "category" to "U",
        "supervisorCategory" to "U",
        // not included - duplicate ID:
        "offender_no" to "G0552UV",
        // not included - duplicate ID:
        "prison_id" to "MDI",
        "created_date" to "2021-05-04T06:58:12.399139Z",
        "approved_date" to "2021-05-04T00:00Z",
        "assessment_committee" to "OCA",
        "assessment_comment" to "steve test 677",
        "next_review_date" to "2021-06-04",
        "placement_prison_id" to "",
        "approved_committee" to "OCA",
        "approved_placement_prison_id" to "",
        "approved_placement_comment" to "",
        "approved_comment" to "steve test 677",
        // not included - system ID:
        "sequence" to "15",
      ),
      "riskProfiler" to mapOf(
        // not included - system ID:
        "offender_no" to "G2515UU",
        "violence" to mapOf(
          // not included - duplicate ID:
          "nomsId" to "G2515UU",
          "numberOfAssaults" to 4,
          "numberOfSeriousAssaults" to 0,
          "numberOfNonSeriousAssaults" to 0,
          "provisionalCategorisation" to "C",
          "shouldNotifySafetyCustodyLead" to "No",
          "isVeryHighRiskViolentOffender" to "No",
        ),
        "dateAndTimeRiskInformationLastUpdated" to "2021-07-27T02:17:48.130833Z",
      ),
    )
  }
}
