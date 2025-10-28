package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient.LocationDetailsResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient.NomisLocationMapping
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository

private const val LOCATION_DPS_ID = "28953d06-d379-450c-9ec4-b5993ce5cd4f"
private const val LOCATION_NOMIS_ID = 4324567

class TemplateDataFetcherFacadeImplTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()

  private val templateDataFetcherFacade = TemplateDataFetcherFacadeImpl(prisonDetailsRepository, userDetailsRepository, locationDetailsRepository, locationsApiClient, nomisMappingApiClient)

  @Nested
  inner class GetPrisonNameTest {
    @Test
    fun `findPrisonNameByPrisonId returns prison name`() {
      whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))
      val response = templateDataFetcherFacade.findPrisonNameByPrisonId("MDI")
      assertThat(response).isEqualTo("Moorland (HMP & YOI)")
    }

    @Test
    fun `findPrisonNameByPrisonId returns null if not found`() {
      val response = templateDataFetcherFacade.findPrisonNameByPrisonId("")
      assertThat(response).isNull()
    }
  }

  @Nested
  inner class FindUserLastNameByUsernameTest {
    @Test
    fun `findUserLastNameByUsername returns user last name`() {
      whenever(userDetailsRepository.findByUsername("AQ987Z")).thenReturn(UserDetail("AQ987Z", "Johnson"))
      val response = templateDataFetcherFacade.findUserLastNameByUsername("AQ987Z")
      assertThat(response).isEqualTo("Johnson")
    }

    @Test
    fun `findUserLastNameByUsername returns null if not found`() {
      val response = templateDataFetcherFacade.findUserLastNameByUsername("AQ987Z")
      assertThat(response).isNull()
    }
  }

  @Nested
  inner class FindLocationNameByDpsIdTest {
    @Test
    fun `findLocationNameByDpsId returns location name from database`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(LocationDetail(LOCATION_DPS_ID, LOCATION_NOMIS_ID, "PROPERTY BOX 27"))
      val response = templateDataFetcherFacade.findLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `findLocationNameByDpsId returns location name from api`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, "PROPERTY BOX 27", "PROP_BOXES-PB027"))
      val response = templateDataFetcherFacade.findLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `findLocationNameByDpsId returns location name from api when no localname value`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, null, "PROP_BOXES-PB027"))
      val response = templateDataFetcherFacade.findLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROP_BOXES-PB027")
    }

    @Test
    fun `findLocationNameByDpsId returns null when not found from api`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(null)
      val response = templateDataFetcherFacade.findLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isNull()
    }
  }

  @Nested
  inner class FindLocationNameByNomisIdTest {
    @Test
    fun `findLocationNameByNomisId returns location name from database`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(LocationDetail(LOCATION_DPS_ID, LOCATION_NOMIS_ID, "PROPERTY BOX 27"))
      val response = templateDataFetcherFacade.findLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `findLocationNameByNomisId returns location name from api`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, "PROPERTY BOX 27", "PROP_BOXES-PB027"))
      val response = templateDataFetcherFacade.findLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `findLocationNameByNomisId returns location name from api when no localname value`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, null, "PROP_BOXES-PB027"))
      val response = templateDataFetcherFacade.findLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROP_BOXES-PB027")
    }

    @Test
    fun `findLocationNameByNomisId returns null when not found from locations api`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(null)
      val response = templateDataFetcherFacade.findLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isNull()
    }

    @Test
    fun `findLocationNameByNomisId returns null when nomis mapping not found`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(null)
      val response = templateDataFetcherFacade.findLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isNull()
    }
  }
}
