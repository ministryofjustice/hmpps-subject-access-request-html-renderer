package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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

  private val templateHashV1 = "2340d53311fcf9aeaadeb6c90020d5ec77db229b342b0e0d088c7dce30eef24c"
  private val templateHashV2 = "ab5dfcf36828754c50e61b440a7b30acef43956579e02230f2d29c837a42a4cc"

  private val serviceConfiguration = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "example-service",
    label = "Example",
    enabled = true,
    templateMigrated = true,
    order = 1,
    url = "https://example.com/",
  )

  private val templateV1Published = TemplateVersion(
    id = UUID.randomUUID(),
    version = 1,
    status = TemplateVersionStatus.PUBLISHED,
    fileHash = templateHashV1,
    createdAt = LocalDateTime.of(2025, 1, 1, 10, 0, 0),
    serviceConfiguration = serviceConfiguration,
  )

  private val templateV2Published = TemplateVersion(
    id = UUID.randomUUID(),
    version = 1,
    status = TemplateVersionStatus.PUBLISHED,
    fileHash = templateHashV1,
    createdAt = LocalDateTime.of(2025, 1, 2, 10, 0, 0),
    serviceConfiguration = serviceConfiguration,
  )

  private val templateV3Pending = TemplateVersion(
    id = UUID.randomUUID(),
    version = 1,
    status = TemplateVersionStatus.PENDING,
    fileHash = templateHashV2,
    createdAt = LocalDateTime.of(2025, 1, 2, 10, 5, 0),
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

    templateVersionRepository.save(templateV1Published)

    assertThat(templateVersionRepository.findByIdOrNull(templateV1Published.id)).isEqualTo(templateV1Published)
  }

  @Test
  fun `should delete template version`() {
    templateVersionRepository.save(templateV1Published)
    assertThat(templateVersionRepository.findByIdOrNull(templateV1Published.id)).isEqualTo(templateV1Published)

    templateVersionRepository.delete(templateV1Published)
    assertThat(templateVersionRepository.findByIdOrNull(templateV1Published.id)).isNull()
  }

  @Nested
  inner class FindLatestByServiceConfigurationId {

    @Test
    fun `should return null if no data exists`() {
      val actual = templateVersionRepository.findLatestByServiceConfigurationId(serviceConfiguration.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return latest template version if only 1 exists`() {
      templateVersionRepository.save(templateV1Published)

      val actual = templateVersionRepository.findLatestByServiceConfigurationId(serviceConfiguration.id)
      assertThat(actual).isEqualTo(templateV1Published)
    }

    @Test
    fun `should return expected template version when multiple versions exist`() {
      templateVersionRepository.saveAll(listOf(templateV1Published, templateV2Published, templateV3Pending))

      val actual = templateVersionRepository.findLatestByServiceConfigurationId(serviceConfiguration.id)
      assertThat(actual).isEqualTo(templateV3Pending)
    }

    @Test
    fun `should return value with higher version number when createdAt fields are equal`() {
      // template V2 has the same createdAt value as V1
      val v1 = TemplateVersion(
        id = UUID.randomUUID(),
        serviceConfiguration = serviceConfiguration,
        status = TemplateVersionStatus.PUBLISHED,
        version = 1,
        createdAt = LocalDateTime.now(),
        fileHash = templateHashV1,
      )
      val v2 = TemplateVersion(
        id = UUID.randomUUID(),
        serviceConfiguration = serviceConfiguration,
        status = TemplateVersionStatus.PUBLISHED,
        version = 2,
        createdAt = v1.createdAt,
        fileHash = templateHashV1,
      )
      templateVersionRepository.saveAll(listOf(v1, v2))

      val actual = templateVersionRepository.findLatestByServiceConfigurationId(serviceConfiguration.id)

      assertThat(actual).isEqualTo(v2)
    }
  }

  @Nested
  inner class FindFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc {

    @Test
    fun `should return null when no records exist`() {
      val actual = templateVersionRepository.findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = UUID.randomUUID(),
        version = 1,
        fileHash = templateHashV1,
        status = TemplateVersionStatus.PUBLISHED,
      )

      assertThat(actual).isNull()
    }

    @Test
    fun `should return expected value when records exist`() {
      templateVersionRepository.saveAll(listOf(templateV1Published, templateV2Published, templateV3Pending))

      val actual = templateVersionRepository.findFirstByIdAndVersionAndFileHashAndStatusOrderByVersionDesc(
        id = templateV3Pending.id,
        version = templateV3Pending.version,
        fileHash = templateV3Pending.fileHash!!,
        status = TemplateVersionStatus.PENDING,
      )

      assertThat(actual).isEqualTo(templateV3Pending)
    }
  }
}
