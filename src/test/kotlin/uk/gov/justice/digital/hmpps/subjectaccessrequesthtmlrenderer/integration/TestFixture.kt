package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import java.time.LocalDate
import java.util.UUID

fun createRenderRequest(port: Int): RenderRequest = RenderRequest(
  id = UUID.randomUUID(),
  serviceName = "hmpps-service-xyz",
  serviceUrl = "http://localhost:$port",
  nomisId = "nomis1234",
  ndeliusId = "ndelius1234",
  dateFrom = LocalDate.of(2020, 1, 1),
  dateTo = LocalDate.of(2021, 1, 1),
)

const val SAR_RESPONSE = """
              {
                "content": {
                  "Service One Property": {
                    "field1": "value1"
                  }
                }
              }
    """
