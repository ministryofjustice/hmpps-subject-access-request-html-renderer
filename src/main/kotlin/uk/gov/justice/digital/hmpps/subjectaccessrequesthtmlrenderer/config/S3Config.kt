package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "s3")
data class S3Properties(
  val region: String,
  val bucketName: String,
  val localStackUrl: String? = "http://localhost:4566",
)

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config(private val s3Properties: S3Properties) {

  @Bean
  @ConditionalOnProperty(name = ["s3.provider"], havingValue = "aws")
  fun s3Client(): S3Client? = runBlocking {
    S3Client.fromEnvironment {
      region = s3Properties.region
    }.also {
      log.info("created AWS S3 client for region ${s3Properties.region}")
    }
  }

  @Bean
  @ConditionalOnProperty(name = ["s3.provider"], havingValue = "localstack")
  fun s3ClientLocalstack(): S3Client? = runBlocking {
    S3Client.fromEnvironment {
      region = s3Properties.region
      endpointUrl = Url.parse(s3Properties.localStackUrl!!)
      forcePathStyle = true
      credentialsProvider = StaticCredentialsProvider {
        accessKeyId = "123456"
        secretAccessKey = "654321"
      }
    }.also {
      log.info(
        "created Localstack S3 client for region: {}, endpointUrl: {}, bucketName: {}",
        s3Properties.region,
        s3Properties.localStackUrl,
        s3Properties.bucketName,
      )
    }.apply { createBucketIfNotExists(this) }
  }

  private suspend fun createBucketIfNotExists(s3: S3Client) {
    try {
      s3.headBucket { bucket = s3Properties.bucketName }
    } catch (ex: NotFound) {
      log.info("bucket {} not found, attempting to create", s3Properties.bucketName)

      s3.createBucket {
        bucket = s3Properties.bucketName
        createBucketConfiguration {
          locationConstraint = BucketLocationConstraint.fromValue(s3Properties.region)
        }
      }

      log.info("bucket {} successfully created", s3Properties.bucketName)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(S3Config::class.java)
  }
}
