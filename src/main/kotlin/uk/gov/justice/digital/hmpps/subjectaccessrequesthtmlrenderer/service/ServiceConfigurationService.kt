package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.ServiceConfigurationRepository
import java.util.UUID

@Service
class ServiceConfigurationService(
  private val serviceConfigurationRepository: ServiceConfigurationRepository,
  @param:Value("\${G1-api.url}") private val g1ApiUrl: String,
  @param:Value("\${G2-api.url}") private val g2ApiUrl: String,
  @param:Value("\${G3-api.url}") private val g3ApiUrl: String,
) {

  fun findByIdOrNull(id: UUID): ServiceConfiguration? = serviceConfigurationRepository.findByIdOrNull(id)

  fun findByIdAndEnabledAndTemplateMigrated(
    id: UUID,
    enabled: Boolean = true,
    templateMigrated: Boolean = true,
  ): ServiceConfiguration? = serviceConfigurationRepository.findByIdAndEnabledAndTemplateMigrated(
    id = id,
    enabled = enabled,
    templateMigrated = templateMigrated,
  )

  fun findByServiceNameOrNull(
    serviceName: String,
  ): ServiceConfiguration? = serviceConfigurationRepository.findByServiceName(serviceName)

  fun resolveUrlPlaceHolder(serviceConfiguration: ServiceConfiguration): String {
    val apiUrl = when (serviceConfiguration.serviceName) {
      "G1" -> g1ApiUrl
      "G2" -> g2ApiUrl
      "G3" -> g3ApiUrl
      else -> serviceConfiguration.url
    }
    return apiUrl
  }
}
