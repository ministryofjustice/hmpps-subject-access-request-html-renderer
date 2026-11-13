package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TempFile
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.nio.file.Files
import java.nio.file.Path

@Service
class RenderWorkspaceService(
  private val renderWorkspaceRootDir: Path = Files.createTempDirectory("sar_render_workspace"),
) {

  private companion object {
    private val log = LoggerFactory.getLogger(RenderWorkspaceService::class.java)
  }

  @PreDestroy
  fun destroy() {
    cleanUp()
  }

  internal fun cleanUp() {
    if (Files.exists(renderWorkspaceRootDir)) {
      log.info("deleting temp directory ${renderWorkspaceRootDir.toAbsolutePath()}")
      renderWorkspaceRootDir.toFile().deleteRecursively()
    }
  }

  fun create(
    renderRequest: RenderRequest,
  ): RenderWorkspace = RenderWorkspace(renderWorkspaceRootDir, renderRequest)

}

class RenderWorkspace(rootDir: Path, renderRequest: RenderRequest) : AutoCloseable {

  private companion object {
    private val log = LoggerFactory.getLogger(RenderWorkspace::class.java)
  }

  private val executionDirectory = Files.createTempDirectory(rootDir, "render-output-")

  val outputFile: TempFile = TempFile(
    Files.createTempFile(
      executionDirectory,
      renderRequest.serviceConfiguration.serviceName,
      ".html",
    ),
  )

  override fun close() {
    if (Files.exists(executionDirectory)) {
      log.info("deleting temp directory ${executionDirectory.toAbsolutePath()}")
      val deleted = executionDirectory.toAbsolutePath().toFile().deleteRecursively()

      if (!deleted && Files.exists(executionDirectory)) {
        log.warn("failed to delete temp directory {}", executionDirectory.toAbsolutePath())
      }
    }
  }
}