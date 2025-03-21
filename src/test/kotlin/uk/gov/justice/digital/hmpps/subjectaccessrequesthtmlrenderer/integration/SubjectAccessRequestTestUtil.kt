package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration

import org.assertj.core.api.Assertions.assertThat
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestException

fun <T : Throwable?> assertExpectedSubjectAccessRequestException(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedCause: Class<T>,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.cause)
    .withFailMessage("actual.cause was null expected type: ${expectedCause.simpleName}")
    .isNotNull
  assertThat(actual.cause)
    .withFailMessage("actual.cause did not match expected type: expected: ${expectedCause.simpleName}, actual: ${actual.cause!!::class.java.simpleName}")
    .isInstanceOf(expectedCause)

  assertException(actual, expectedPrefix, expectedParams)
}

fun assertExpectedSubjectAccessRequestExceptionWithCauseNull(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.cause).isNull()

  assertException(actual, expectedPrefix, expectedParams)
}

private fun assertException(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.message)
    .startsWith(expectedPrefix)

  when (expectedParams) {
    null -> assertThat(actual.params).isNull()
    else -> assertThat(actual.params)
      .containsExactlyInAnyOrderEntriesOf(expectedParams)
  }
}
