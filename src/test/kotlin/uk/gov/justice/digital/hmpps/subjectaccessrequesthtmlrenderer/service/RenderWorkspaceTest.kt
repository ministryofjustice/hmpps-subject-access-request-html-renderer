package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

class RenderWorkspaceTest {

  @TempDir
  lateinit var tempDir: Path

  lateinit var workspaceRootDir: Path

  private val serviceConfiguration: ServiceConfiguration = mock()

  private val renderRequest: RenderRequest = mock()

  @BeforeEach
  fun setUp() {
    workspaceRootDir = Files.createDirectories(tempDir.resolve("workspace_root"))

    whenever(renderRequest.serviceConfiguration).thenReturn(serviceConfiguration)
    whenever(serviceConfiguration.serviceName).thenReturn("hmpps-test-service")
  }

  @Test
  fun `should create the expected request directories and files`() {
    RenderWorkspace(workspaceRootDir, renderRequest).use { workspace ->
      assertThat(workspaceRootDir).exists()

      assertThat(workspace.executionDirectory).exists()
      assertThat(workspace.executionDirectory.parent).isEqualTo(workspaceRootDir)
      assertThat(workspace.executionDirectory).isDirectory()
      assertThat(workspace.executionDirectory.name).startsWith("render-workspace-")

      assertThat(workspace.outputFile).isNotNull
      assertThat(workspace.outputFile.path).isNotNull
      assertThat(workspace.outputFile.path).exists()
      assertThat(workspace.outputFile.path.parent).isEqualTo(workspace.executionDirectory)
      assertThat(workspace.outputFile.path).isRegularFile
      assertThat(workspace.outputFile.path.name).startsWith("hmpps-test-service-")
      assertThat(workspace.outputFile.path.extension).isEqualTo("html")
    }
  }

  @Test
  fun `should create unique workspace for identical renderRequests`() {
    val first = RenderWorkspace(workspaceRootDir, renderRequest)
    val second = RenderWorkspace(workspaceRootDir, renderRequest)

    assertThat(first.outputFile.path).isNotEqualTo(second.outputFile.path)

    first.close()
    second.close()
  }

  @Test
  fun `close should delete workspace directory and outputFile`() {
    val workspace = RenderWorkspace(workspaceRootDir, renderRequest)
    assertThat(workspace.executionDirectory).exists()
    assertThat(workspace.outputFile.path).exists()

    workspace.close()
    assertThat(workspace.outputFile.path).doesNotExist()
    assertThat(workspace.executionDirectory).doesNotExist()

    assertThat(workspaceRootDir).isEmptyDirectory
  }

  @Test
  fun `close is safe to call multiple times`() {
    val workspace = RenderWorkspace(workspaceRootDir, renderRequest)
    assertThat(workspace.outputFile.path).exists()
    workspace.close()

    assertDoesNotThrow { workspace.close() }
  }
}
