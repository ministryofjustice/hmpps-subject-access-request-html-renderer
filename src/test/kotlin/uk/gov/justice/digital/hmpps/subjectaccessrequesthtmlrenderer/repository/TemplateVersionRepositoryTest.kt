package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceCategory
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

  companion object {

    data class FindLatestTestCase(
      val desc: String,
      val templateVersions: List<TemplateVersion>,
      val serviceConfigId: UUID,
      val expected: TemplateVersion?,
    ) {
      override fun toString(): String = desc
    }

    private val templateHashV1 = "2340d53311fcf9aeaadeb6c90020d5ec77db229b342b0e0d088c7dce30eef24c"
    private val templateHashV2 = "ab5dfcf36828754c50e61b440a7b30acef43956579e02230f2d29c837a42a4cc"

    private val serviceConfiguration = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = "example-service",
      label = "Example",
      enabled = true,
      templateMigrated = true,
      url = "https://example.com/",
      category = ServiceCategory.PRISON,
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




    val v1Published = TemplateVersion(
      id = UUID.randomUUID(),
      version = 1,
      status = TemplateVersionStatus.PUBLISHED,
      fileHash = "v1-published-hash",
      createdAt = LocalDateTime.of(2025, 1, 1, 10, 1, 0),
      serviceConfiguration = serviceConfiguration,
    )

    val v1Pending = TemplateVersion(
      id = UUID.randomUUID(),
      version = 1,
      status = TemplateVersionStatus.PENDING,
      fileHash = "v1-pending-hash",
      createdAt = LocalDateTime.of(2025, 1, 1, 10, 1, 30),
      serviceConfiguration = serviceConfiguration,
    )

    val v2Published = TemplateVersion(
      id = UUID.randomUUID(),
      version = 1,
      status = TemplateVersionStatus.PUBLISHED,
      fileHash = "v2-published-hash",
      createdAt = LocalDateTime.of(2025, 1, 1, 10, 2, 0),
      serviceConfiguration = serviceConfiguration,
    )

    val v2Pending = TemplateVersion(
      id = serviceConfiguration.id,
      version = 1,
      status = TemplateVersionStatus.PUBLISHED,
      fileHash = "v2-published-hash",
      createdAt = LocalDateTime.of(2025, 1, 1, 10, 2, 30),
      serviceConfiguration = serviceConfiguration,
    )

    val v3Pending = TemplateVersion(
      id = UUID.randomUUID(),
      version = 1,
      status = TemplateVersionStatus.PENDING,
      fileHash = "v3-pending-hash",
      createdAt = LocalDateTime.of(2025, 1, 1, 10, 3, 0),
      serviceConfiguration = serviceConfiguration,
    )

    @JvmStatic
    fun findLatestPublishedTestCases() = listOf(
      FindLatestTestCase(
        desc = "no template versions exist",
        templateVersions = emptyList(),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
      FindLatestTestCase(
        desc = "not version exist for service config id",
        templateVersions = listOf(v1Published, v2Published),
        serviceConfigId = UUID.randomUUID(),
        expected = null,
      ),
      FindLatestTestCase(
        desc = "a single template version exist",
        templateVersions = listOf(v1Published),
        serviceConfigId = serviceConfiguration.id,
        expected = v1Published,
      ),
      FindLatestTestCase(
        desc = "multiple published versions exist",
        templateVersions = listOf(v1Published, v2Published),
        serviceConfigId = serviceConfiguration.id,
        expected = v2Published,
      ),
      FindLatestTestCase(
        desc = "a published version exist and a pending version exists",
        templateVersions = listOf(v1Published, v3Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v1Published,
      ),
      FindLatestTestCase(
        desc = "multiple published versions exist and a pending version exists",
        templateVersions = listOf(v1Published, v2Published, v3Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v2Published,
      ),
      FindLatestTestCase(
        desc = "only a pending version exists",
        templateVersions = listOf(v3Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
    )

    @JvmStatic
    fun findLatestPendingTestCases() = listOf(
      FindLatestTestCase(
        desc = "no template versions exist",
        templateVersions = emptyList(),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
      FindLatestTestCase(
        desc = "only a published version exist",
        templateVersions = listOf(v1Published),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
      FindLatestTestCase(
        desc = "a pending version exists created after latest published version",
        templateVersions = listOf(v1Published, v1Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v1Pending,
      ),
      FindLatestTestCase(
        desc = "multiple pending versions exist",
        templateVersions = listOf(v1Published, v1Pending, v2Published, v3Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v3Pending,
      ),
      FindLatestTestCase(
        desc = "latest pending has been superseded",
        templateVersions = listOf(v1Published, v1Pending, v2Published),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
      FindLatestTestCase(
        desc = "there is no published version",
        templateVersions = listOf(v1Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v1Pending,
      ),
      FindLatestTestCase(
        desc = "multiple published versions",
        templateVersions = listOf(v1Published, v2Published),
        serviceConfigId = serviceConfiguration.id,
        expected = null,
      ),
      FindLatestTestCase(
        desc = "multiple pending versions no published versions",
        templateVersions = listOf(v1Pending, v2Pending, v3Pending),
        serviceConfigId = serviceConfiguration.id,
        expected = v3Pending,
      ),
    )
  }

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
  inner class FindLatestByServiceConfigurationIdAndStatus {

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepositoryTest#findLatestPublishedTestCases")
    fun `should return the expected published template value`(tc: FindLatestTestCase) {
      templateVersionRepository.saveAll(tc.templateVersions)

      val actual = templateVersionRepository.findLatestPublishedByServiceConfigurationId(tc.serviceConfigId)
      assertThat(actual).isEqualTo(tc.expected)
    }
  }

  @Nested
  inner class FindLatestPendingByServiceConfigurationId {
    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.TemplateVersionRepositoryTest#findLatestPendingTestCases")
    fun `should return the expected pending template value`(tc: FindLatestTestCase) {
      templateVersionRepository.saveAll(tc.templateVersions)

      val actual = templateVersionRepository.findLatestPendingByServiceConfigurationId(tc.serviceConfigId)
      assertThat(actual).isEqualTo(tc.expected)
    }
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
