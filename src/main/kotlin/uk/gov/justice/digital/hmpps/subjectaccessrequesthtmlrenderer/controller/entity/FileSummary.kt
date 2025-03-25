package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity

import aws.smithy.kotlin.runtime.time.Instant

data class FileSummary(val files: List<FileInfo>)

/**
 * Class represents metadata about a subject access request file held in the document store
 */
data class FileInfo(val key: String? = "no-value", val lastModified: Instant? = null, val size: Long? = null)
