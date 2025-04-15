package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templateGenerator

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateResources
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates.TemplateService
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.notExists

/**
 * Developer util class to aid template development/testing.
 */
fun main(args: Array<String>?) {
  if (args.isNullOrEmpty()) {
    throw IllegalArgumentException("serviceName argument required")
  }

  TemplateGeneratorUtil().generateServiceHtml(args[0])
}

class TemplateGeneratorUtil {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val templateResources: TemplateResources = TemplateResources()

  private val templateHelpers = TemplateHelpers(
    prisonDetailsRepository,
    userDetailsRepository,
    locationDetailsRepository,
    locationsApiClient,
    nomisMappingApiClient,
  )

  private val templateService: TemplateService = TemplateService(templateHelpers, templateResources, telemetryClient)

  init {
    whenever(userDetailsRepository.findByUsername(any())).doAnswer {
      val input = it.arguments[0] as String
      UserDetail(input, "Homer Simpson")
    }

    whenever(prisonDetailsRepository.findByPrisonId(any())).doAnswer {
      PrisonDetail(it.arguments[0] as String, "HMPPS Mordor")
    }

    whenever(locationDetailsRepository.findByDpsId(any())).doAnswer {
      val dpsId = it.arguments[0] as String
      LocationDetail(dpsId, 666, "Hogwarts")
    }
  }

  fun generateServiceHtml(serviceName: String) {
    println()
    log("Rendering templates for $serviceName")

    try {
      val renderRequest = RenderRequest(id = UUID.randomUUID(), serviceName = serviceName)
      val output = renderHtml(renderRequest).use { os -> writeToFile(serviceName, os) }

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

  private fun renderHtml(renderRequest: RenderRequest): ByteArrayOutputStream = templateService
    .renderServiceDataHtml(
      renderRequest = renderRequest,
      data = getServiceResponseStubData(renderRequest.serviceName!!),
    ) ?: throw renderedTemplateNullException(renderRequest.serviceName!!)

  private fun getServiceResponseStubData(serviceName: String): ArrayList<*>? {
    val path = assertResponseStubJsonExists(serviceName)
    log("Using response stub data: file:///$path ")

    return FileInputStream(path.toFile()).use { inputStream ->
      inputStream.use {
        ObjectMapper().readValue(inputStream, Map::class.java)
      }
    }.let { it["content"] as ArrayList<*>? }
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
}
