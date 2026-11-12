package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TempFile
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.rendering.RenderRequest
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

@Service
class TempFileService(
  val templateRenderOutputDir: Path = createTempDirectory("sar_template_renderer_output"),
) {

  private companion object {
    private val log = LoggerFactory.getLogger(TempFileService::class.java)
  }

  @PreDestroy
  fun destroy() {
    cleanUp()
  }

  internal fun cleanUp() {
    if (Files.exists(templateRenderOutputDir)) {
      log.info("deleting temp directory ${templateRenderOutputDir.toAbsolutePath()}")
      templateRenderOutputDir.toFile().deleteRecursively()
    }
  }

  fun createTempFile(
    renderRequest: RenderRequest,
  ): TempFile = TempFile(createRenderOutputFile(renderRequest)).also {
    log.info("created temp file {}", it)
  }

  private fun createRenderOutputFile(
    renderRequest: RenderRequest,
  ): Path {
    val directoryPath = Files.createDirectories(
      templateRenderOutputDir
        .resolve(renderRequest.id.toString())
        .resolve(System.currentTimeMillis().toString()),
    )

    return Files.createTempFile(directoryPath, renderRequest.serviceConfiguration.serviceName, ".html")
  }
}
