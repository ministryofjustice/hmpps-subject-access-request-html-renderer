package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templateGenerator

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateResources
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateService
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.notExists

/**
 * Developer util class to aid template development/testing.
 */
fun main(args: Array<String>?) {
  if (args.isNullOrEmpty()) {
    throw IllegalArgumentException("serviceName argument required")
  }

  TemplateGeneratorUtil().generateServiceHtml(args[0], args[1].toBoolean())
}

class TemplateGeneratorUtil {
  private val telemetryClient: TelemetryClient = mock()
  private val templateDataFetcherFacade: TemplateDataFetcherFacade = mock()

  private val templateResources: TemplateResources = TemplateResources()

  private val templateHelpers = TemplateHelpers(templateDataFetcherFacade)

  private val templateRenderService: TemplateRenderService = TemplateRenderService(templateHelpers)

  private val templateService: TemplateService = TemplateService(templateRenderService, templateResources, telemetryClient)

  init {
    whenever(templateDataFetcherFacade.findUserLastNameByUsername(any())).thenReturn("Homer Simpson")
    whenever(templateDataFetcherFacade.findPrisonNameByPrisonId(any())).thenReturn("HMPPS Mordor")
    whenever(templateDataFetcherFacade.findLocationNameByNomisId(any())).thenReturn("Hogwarts")
    whenever(templateDataFetcherFacade.findLocationNameByDpsId(any())).thenReturn("Hogwarts")
  }

  fun generateServiceHtml(serviceName: String, isNullData: Boolean) {
    println()
    log("Rendering templates for $serviceName")

    try {
      val renderRequest = RenderRequest(id = UUID.randomUUID(), serviceName = serviceName, serviceLabel = getServiceLabel(serviceName))
      val output = renderHtml(renderRequest, isNullData).use { os -> writeToFile(serviceName, os) }

      println()
      log("Successfully generated HTML for service $serviceName:")
      log("file:///$output\n")
    } catch (e: Exception) {
      log("failed to render template for service $serviceName, ${e.message}")
      throw e
    }
  }

  private fun writeToFile(serviceName: String, os: ByteArrayOutputStream): Path {
    val output = getOutputFile(serviceName)
    Files.write(output, os.toByteArray())
    return output
  }

  private fun renderHtml(renderRequest: RenderRequest, isNullData: Boolean): ByteArrayOutputStream {
    val data = if (isNullData) null else getServiceResponseStubData(renderRequest.serviceName!!)

    return templateService.renderServiceDataHtml(renderRequest = renderRequest, data = data)
      ?: throw renderedTemplateNullException(renderRequest.serviceName!!)
  }

  private fun getServiceResponseStubData(serviceName: String): Any? {
    val path = assertResponseStubJsonExists(serviceName)
    log("Using response stub data: file:///$path ")

    return FileInputStream(path.toFile()).use { inputStream ->
      inputStream.use {
        ObjectMapper().readValue(inputStream, Map::class.java)
      }
    }.let { it["content"] }
  }

  private fun assertResponseStubJsonExists(serviceName: String): Path {
    val target = getResponseJsonStub(serviceName)

    if (target.notExists()) {
      throw responseStubJsonNotFoundException(target)
    }

    return target
  }

  private fun getOutputFile(serviceName: String) = Paths.get(getTestResourcesDir())
    .resolve("integration-tests/reference-html-stubs/$serviceName-expected.html")

  private fun getResponseJsonStub(serviceName: String) = Paths.get(getTestResourcesDir())
    .resolve("integration-tests.service-response-stubs/$serviceName-response.json")

  private fun getTestResourcesDir() = System.getenv("TEST_RESOURCES_DIR")

  private fun renderedTemplateNullException(serviceName: String) = RuntimeException("Rendered HTML for service $serviceName was null")

  private fun responseStubJsonNotFoundException(target: Path) = RuntimeException("response stub json file $target not found")

  private fun log(message: String) {
    println("[generateTemplate] $message")
  }

  // Add services if missing.
  private val serviceMapping = mapOf(
    "hmpps-offender-categorisation-api" to "Categorisation Tool",
  )

  private fun getServiceLabel(serviceName: String) = serviceMapping[serviceName] ?: "Unknown".also {
    log("Could not find service label for service $serviceName, please add service to TemplateGeneratorUtil.serviceMapping map")
  }
}
