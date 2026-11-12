package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import kotlin.io.path.name

@ExtendWith(MockitoExtension::class)
class TempFileServiceTest {

  @TempDir
  lateinit var baseDir: Path

  private lateinit var sarDir: Path

  private lateinit var tempFileService: TempFileService

  private val serviceConfiguration: ServiceConfiguration = mock()

  private val renderRequest: RenderRequest = RenderRequest(
    id = UUID.randomUUID(),
    nomisId = null,
    ndeliusId = null,
    dateFrom = LocalDate.now().minusDays(1),
    dateTo = LocalDate.now(),
    sarCaseReferenceNumber = "A1234AA",
    serviceConfiguration = serviceConfiguration,
  )

  @BeforeEach
  fun setup() {
    sarDir = baseDir.resolve("sar")
    tempFileService = TempFileService(sarDir)

    whenever(serviceConfiguration.serviceName).thenReturn("test-service")
  }

  @Test
  fun `should create a temporary file with expected structure`() {
    tempFileService.createTempFile(renderRequest).use { tempFile ->
      assertThat(tempFile.path).exists()

      // Filename matches service name pattern
      assertThat(tempFile.path.name).startsWith("test-service").endsWith(".html")

      // The parent timestamp dir exists
      assertThat(tempFile.path.parent).exists()

      // The parent of the timestamp dir matches the request ID
      assertThat(tempFile.path.parent.parent.name).isEqualTo(renderRequest.id.toString())

      // The parent of the request ID dir is the SAR base dir
      assertThat(tempFile.path.parent.parent.parent).isEqualTo(sarDir)
    }
  }

  @Test
  fun `should create unique files for requests with the same details`() {
    val first = tempFileService.createTempFile(renderRequest)
    assertThat(first.path).exists()

    val second = tempFileService.createTempFile(renderRequest)
    assertThat(second.path).exists()

    assertThat(first.path).isNotEqualTo(second.path)
  }

  @Test
  fun `clean up should delete all files and directories`() {
    val first = tempFileService.createTempFile(renderRequest)
    assertThat(first.path).exists()

    val second = tempFileService.createTempFile(renderRequest)
    assertThat(second.path).exists()

    tempFileService.cleanUp()

    assertThat(sarDir).doesNotExist()
  }

  @Test
  fun `cleanup is safe to call when directory is already deleted`() {
    tempFileService.createTempFile(renderRequest)

    tempFileService.cleanUp()

    assertThatNoException().isThrownBy {
      tempFileService.cleanUp()
    }
  }
}
