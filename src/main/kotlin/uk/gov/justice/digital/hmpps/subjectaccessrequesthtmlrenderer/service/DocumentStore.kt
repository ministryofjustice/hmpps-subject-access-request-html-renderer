package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.controller.entity.RenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.exception.SubjectAccessRequestDocumentStorageException

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

  suspend fun add(renderRequest: RenderRequest, data: ByteArray?) {
    try {
      s3.putObject {
        bucket = s3Properties.bucketName
        key = renderRequest.documentKey()
        body = ByteStream.fromBytes(data ?: byteArrayOf()) // default to empty if null TODO check if this is right
      }

      log.info("adding document to document store.... ${renderRequest.documentKey()}")
    } catch (ex: Exception) {
      throw SubjectAccessRequestDocumentStorageException(
        subjectAccessRequestId = renderRequest.id,
        message = "failed to upload document",
        params = mapOf("documentKey" to renderRequest.documentKey()),
      )
    }
  }
}
