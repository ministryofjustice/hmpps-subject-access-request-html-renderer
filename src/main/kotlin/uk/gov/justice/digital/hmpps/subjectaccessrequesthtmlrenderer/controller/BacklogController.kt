package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.SubjectDataHeldRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.SubjectDataHeldResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service.DataHeldService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_USER_ACCESS', 'ROLE_SAR_DATA_ACCESS', 'ROLE_SAR_SUPPORT')")
@RequestMapping(path = ["/subject-access-request/"], produces = ["application/json"])
class BacklogController(
  private val dataHeldService: DataHeldService,
) {

  @Operation(
    summary = "Get a data held summary for the the subject",
    description = "Get a data held summary for the the subject",
    security = [SecurityRequirement(name = "subject-access-request-html-renderer-ui-role")],
    responses = [
      ApiResponse(responseCode = "200", description = "Data is held by service on subject"),
      ApiResponse(
        responseCode = "404",
        description = "file not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PostMapping("/subject-data-held-summary")
  fun getDataHeldSummary(
    @RequestBody request: SubjectDataHeldRequest,
  ): ResponseEntity<SubjectDataHeldResponse> = ResponseEntity(
    SubjectDataHeldResponse(
      nomisId = request.nomisId,
      ndeliusId = request.ndeliusId,
      dataHeld = dataHeldService.isSubjectDataHeld(request),
      serviceName = request.service!!.name,
    ),
    HttpStatus.OK,
  )
}
