package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception

/**
 * Render API response error codes
 * - 1xxx range: General catch all error codes.
 * - 2xxx range: Internal errors
 * - 3xxx range: Service Template errors
 * - 4xxx range: Document store errors
 */
enum class ErrorCode(val code: Int) {

  INTERNAL_SERVER_ERROR(1000),

  BAD_REQUEST(1001),

  NOT_FOUND(1002),

  WEB_CLIENT_RETRY_EXHAUSTED(2000),

  WEB_CLIENT_NON_RETRYABLE_ERROR(2001),

  SERVICE_CONFIGURATION_NOT_FOUND(2002),

  TEMPLATE_RESOURCE_NOT_FOUND(3000),

  SERVICE_TEMPLATE_HASH_MISMATCH(3001),

  SERVICE_TEMPLATE_EMPTY(3002),

  SERVICE_TEMPLATE_NOT_FOUND(3003),

  SERVICE_TEMPLATE_PUBLISH_FAILURE(3004),

  DOCUMENT_STORE_JSON_UPLOAD_FAILED(4000),

  DOCUMENT_STORE_HTML_UPLOAD_FAILED(4001),

  DOCUMENT_STORE_ATTACHMENT_UPLOAD_FAILED(4002),

  DOCUMENT_STORE_FILE_NOT_FOUND(4003);

  fun codeString(): String = this.code.toString()
}