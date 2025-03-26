package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.time.toJvmInstant
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.TestComponent
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config.S3Properties
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.integration.IntegrationTestBase.S3File
import java.time.Instant

@TestComponent
class S3TestUtil(
  val s3: S3Client,
  val s3Properties: S3Properties,
) {

  fun getFile(documentKey: String): String = runBlocking {
    s3.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = documentKey
      },
    ) { it.body?.toByteArray() }?.let { String(it) } ?: ""
  }

  fun clearBucket() = runBlocking {
    s3.listObjectsV2 { bucket = s3Properties.bucketName }
      .contents
      ?.map { ObjectIdentifier { key = it.key } }
      ?.takeIf { it.isNotEmpty() }
      ?.let { identifiers ->
        s3.deleteObjects {
          bucket = s3Properties.bucketName
          delete = Delete {
            objects = identifiers
          }
        }
      }
  }

  fun addFilesToBucket(vararg files: S3File) = runBlocking {
    files.forEach {
      addFileToBucket(it)
    }
  }

  fun addFileToBucket(file: S3File) = runBlocking {
    s3.putObject {
      bucket = s3Properties.bucketName
      key = file.key
      body = ByteStream.fromString(file.content)
    }
  }

  fun documentExists(key: String): Boolean = runBlocking {
    try {
      s3.headObject {
        this.bucket = s3Properties.bucketName
        this.key = key
      }
      true
    } catch (e: NotFound) {
      false
    }
  }

  fun getFileMetadata(key: String): FileMetadata = runBlocking {
    s3.getObject(
      GetObjectRequest {
        this.bucket = s3Properties.bucketName
        this.key = key
      },
    ) { FileMetadata(it.eTag, it.lastModified?.toJvmInstant()) }
  }

  data class FileMetadata(val eTag: String?, val lastModified: Instant?)
}
