package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import java.util.UUID

@DataJpaTest
class ServiceConfigurationRepositoryTest @Autowired constructor(
  val serviceConfigurationRepository: ServiceConfigurationRepository,
) {

  private val serviceConfiguration = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "example-service",
    label = "Example",
    enabled = true,
    templateMigrated = true,
    order = 1,
    url = "https://example.com/",
  )

  @BeforeEach
  fun beforeEach() {
    serviceConfigurationRepository.deleteAll()
  }

  @AfterEach
  fun afterEach() {
    serviceConfigurationRepository.deleteAll()
  }

  @Test
  fun `should save service configuration`() {
    serviceConfigurationRepository.save(serviceConfiguration)

    val actual = serviceConfigurationRepository.findAll()
    assertThat(actual).hasSize(1)
    assertThat(actual.first()).isEqualTo(serviceConfiguration)
  }

  @Test
  fun `should delete service configuration`() {
    serviceConfigurationRepository.save(serviceConfiguration)
    assertThat(serviceConfigurationRepository.findAll()).hasSize(1)

    serviceConfigurationRepository.delete(serviceConfiguration)
    assertThat(serviceConfigurationRepository.findAll()).hasSize(0)
  }

  @Test
  fun `should find service configuration by ID`() {
    serviceConfigurationRepository.save(serviceConfiguration)

    assertThat(serviceConfigurationRepository.findByIdOrNull(serviceConfiguration.id)).isEqualTo(serviceConfiguration)
  }
}
