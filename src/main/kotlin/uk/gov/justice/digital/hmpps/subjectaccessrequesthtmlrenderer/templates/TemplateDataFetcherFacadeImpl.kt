package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository

@Service
class TemplateDataFetcherFacadeImpl(
  private val prisonDetailsRepository: PrisonDetailsRepository,
  private val userDetailsRepository: UserDetailsRepository,
  private val locationDetailsRepository: LocationDetailsRepository,
  private val locationsApiClient: LocationsApiClient,
  private val nomisMappingApiClient: NomisMappingApiClient,
) : TemplateDataFetcherFacade {

  override fun findPrisonNameByPrisonId(prisonId: String): String? = prisonDetailsRepository
    .findByPrisonId(prisonId)?.prisonName

  override fun findUserLastNameByUsername(userId: String): String? = userDetailsRepository
    .findByUsername(userId)?.lastName

  override fun findLocationNameByNomisId(nomisId: Int): String? = locationDetailsRepository.findByNomisId(nomisId)?.name
    ?: nomisMappingApiClient.getNomisLocationMapping(nomisId)?.let { findLocationNameByDpsId(it.dpsLocationId) }

  override fun findLocationNameByDpsId(dpsId: String): String? = locationDetailsRepository.findByDpsId(dpsId)?.name
    ?: locationsApiClient.getLocationDetails(dpsId)?.let { it.localName ?: it.pathHierarchy }
}
