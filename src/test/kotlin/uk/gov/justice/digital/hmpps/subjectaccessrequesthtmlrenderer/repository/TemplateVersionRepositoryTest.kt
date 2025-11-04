package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.TemplateVersionStatus
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
class TemplateVersionRepositoryTest @Autowired constructor(
  val templateVersionRepository: TemplateVersionRepository,
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

  private val templateVersion1 = TemplateVersion(
    id = UUID.randomUUID(),
    version = 1,
    status = TemplateVersionStatus.PENDING,
    fileHash = "123456",
    createdAt = LocalDateTime.now(),
    serviceConfiguration = serviceConfiguration,
  )

  @BeforeEach
  fun setUp() {
    templateVersionRepository.deleteAll()
    serviceConfigurationRepository.deleteAll()
    serviceConfigurationRepository.save(serviceConfiguration)
  }

  @Test
  fun `should save template version`() {
    assertThat(templateVersionRepository.findAll()).isEmpty()

    templateVersionRepository.save(templateVersion1)

    assertThat(templateVersionRepository.findByIdOrNull(templateVersion1.id)).isEqualTo(templateVersion1)
  }

  @Test
  fun `should delete template version`() {
    templateVersionRepository.save(templateVersion1)
    assertThat(templateVersionRepository.findByIdOrNull(templateVersion1.id)).isEqualTo(templateVersion1)

    templateVersionRepository.delete(templateVersion1)
    assertThat(templateVersionRepository.findByIdOrNull(templateVersion1.id)).isNull()
  }
}
