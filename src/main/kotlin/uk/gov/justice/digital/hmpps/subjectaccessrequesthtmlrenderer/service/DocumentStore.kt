package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.time.toJvmInstant
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.client.Attachment
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.FileInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestDocumentStorageException
import java.util.UUID

@Service
class DocumentStore(
  private val s3: S3Client,
  private val s3Properties: S3Properties,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(DocumentStore::class.java)
  }

  suspend fun contains(documentKey: String): Boolean {
    val result = try {
      s3.headObject {
        this.bucket = s3Properties.bucketName
        this.key = documentKey
      }
      true
    } catch (ex: NotFound) {
      false
    }

    return result.also {
      log.info("subject access request html partial: $documentKey exists? $it")
    }
  }

  suspend fun addJson(renderRequest: RenderRequest, data: ByteArray?) {
    try {
      s3.putObject {
        bucket = s3Properties.bucketName
        key = renderRequest.documentJsonKey()
        body = ByteStream.fromBytes(data ?: byteArrayOf()) // default to empty if null TODO check if this is right
      }

      log.info("adding json document to document store.... ${renderRequest.documentJsonKey()}")
    } catch (ex: Exception) {
      throw SubjectAccessRequestDocumentStorageException(
        subjectAccessRequestId = renderRequest.id,
        message = "failed to upload json document",
        params = mapOf("documentKey" to renderRequest.documentJsonKey()),
      )
    }
  }

  suspend fun addHtml(renderRequest: RenderRequest, data: ByteArray?) {
    try {
      s3.putObject {
        bucket = s3Properties.bucketName
        key = renderRequest.documentHtmlKey()
        body = ByteStream.fromBytes(data ?: byteArrayOf()) // default to empty if null TODO check if this is right
      }

      log.info("adding html document to document store.... ${renderRequest.documentHtmlKey()}")
    } catch (ex: Exception) {
      throw SubjectAccessRequestDocumentStorageException(
        subjectAccessRequestId = renderRequest.id,
        message = "failed to upload html document",
        params = mapOf("documentKey" to renderRequest.documentHtmlKey()),
      )
    }
  }

  suspend fun addAttachment(renderRequest: RenderRequest, attachment: Attachment, data: ByteArray) {
    try {
      s3.putObject {
        bucket = s3Properties.bucketName
        key = renderRequest.documentAttachmentKey(attachment.filename)
        contentType = attachment.contentType
        contentLength = attachment.filesize.toLong()
        metadata = mapOf(
          "x-amz-meta-filename" to attachment.filename,
          "x-amz-meta-attachment-number" to attachment.attachmentNumber.toString(),
          "x-amz-meta-name" to attachment.name,
        )
        body = ByteStream.fromBytes(data)
      }

      log.info("adding attachment to document store.... ${renderRequest.documentHtmlKey()}")
    } catch (ex: Exception) {
      throw SubjectAccessRequestDocumentStorageException(
        subjectAccessRequestId = renderRequest.id,
        message = "failed to upload attachment",
        params = mapOf("documentKey" to renderRequest.documentHtmlKey(), "filename" to attachment.filename),
      )
    }
  }

  suspend fun getByDocumentKey(documentKey: String): ByteArray? {
    val request = GetObjectRequest {
      bucket = s3Properties.bucketName
      key = documentKey
    }

    return try {
      s3.getObject(request) { it.body?.toByteArray() ?: ByteArray(0) }
    } catch (notFound: NoSuchKey) {
      null
    } catch (ex: Exception) {
      throw SubjectAccessRequestDocumentStorageException(
        message = "failed to get document from bucket",
        params = mapOf("documentKey" to documentKey),
        cause = ex,
      )
    }
  }

  suspend fun list(subjectAccessRequestId: UUID): List<FileInfo>? = s3.listObjectsV2 {
    bucket = s3Properties.bucketName
    prefix = subjectAccessRequestId.toString()
  }.contents
    ?.filter { StringUtils.isNotEmpty(it.key) && it.key!!.endsWith(suffix = ".html") }
    ?.map { FileInfo(key = it.key, lastModified = it.lastModified?.toJvmInstant(), size = it.size) }
}
