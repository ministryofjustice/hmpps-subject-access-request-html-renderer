package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.RenderService

/**
 * Endpoint to retrieve generated html files from the backing S3 bucket. Intended as a developer helper tool to assist
 * the service template creation process.
 *
 * The endpoint is conditional defaulting to false/disabled and must be explicitly enabled. It should NOT be enabled in
 * the Preprod or Production environments.
 */
@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request"], produces = ["text/plain"])
@ConditionalOnExpression("\${developer-endpoint.enabled:false}")
class DeveloperController(private val renderService: RenderService) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @GetMapping("/partials/{documentKey}", produces = ["text/plain"])
  suspend fun getRenderedHtml(@PathVariable documentKey: String) = renderService.getRenderedHtml(documentKey)?.let {
    return ResponseEntity(String(it), null, HttpStatus.OK)
  } ?: run {
    log.info("requested document '$documentKey' not found")
    ResponseEntity<String>(HttpStatus.NOT_FOUND)
  }
}
