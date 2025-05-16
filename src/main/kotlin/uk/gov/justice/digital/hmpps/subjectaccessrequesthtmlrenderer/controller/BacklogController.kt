package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.DataHeldSummaryRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.DataHeldSummaryResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.DataHeldService

@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request/"], produces = ["application/json"])
class BacklogController(
  private val dataHeldService: DataHeldService,
) {

  @PostMapping("/data-held-summary")
  fun getDataHeldSummary(@RequestBody request: DataHeldSummaryRequest): ResponseEntity<DataHeldSummaryResponse> {
    val summary = dataHeldService.getDataHeldSummary(request)

    return ResponseEntity(
      DataHeldSummaryResponse(
        nomisId = request.nomisId,
        ndeliusId = request.ndeliusId,
        dataHeld = summary.isDataHeld(),
        dataHeldSummary = summary,
      ),
      HttpStatus.OK,
    )
  }

  /**
   * Global Data is held flag is true if at least 1 service holds data on the subject.
   */
  fun List<ServiceSummary>.isDataHeld(): Boolean = this.firstOrNull { it.dataHeld }?.let { true } ?: false
}
