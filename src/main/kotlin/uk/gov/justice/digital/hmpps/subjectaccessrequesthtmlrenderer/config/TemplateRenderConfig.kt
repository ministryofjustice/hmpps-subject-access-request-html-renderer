package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.FileBasedTemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.attachments.EmbeddedInlineAttachmentRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.attachments.InlineAttachmentRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.documentstore.DocumentStore
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments.InlineAttachmentFileService
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments.S3InlineAttachmentRenderer
import java.util.concurrent.TimeUnit

@Configuration
class TemplateRenderConfig(
  @param:Value("\${template-helper-cache.maximum-size:10000}") val maximumSize: Long,
  @param:Value("\${template-helper-cache.expire-after-minutes:10}") val expireAfter: Long,
) {
  companion object {
    private val log = LoggerFactory.getLogger(TemplateRenderConfig::class.java)
  }

  @Bean
  @ConditionalOnProperty(
    prefix = "inline-attachment-renderer",
    name = ["use"],
    havingValue = "s3-renderer",
    matchIfMissing = false,
  )
  fun s3InlineAttachmentRenderer(
    @Qualifier("CachedTemplateDataFetcherFacadeImpl") templateDataFetcherFacade: TemplateDataFetcherFacade,
    objectMapper: ObjectMapper,
    documentStore: DocumentStore,
    inlineAttachmentFileService: InlineAttachmentFileService,
  ): InlineAttachmentRenderer = S3InlineAttachmentRenderer(
    templateDataFetcherFacade,
    objectMapper,
    documentStore,
    inlineAttachmentFileService,
  )

  @Bean
  @ConditionalOnProperty(
    prefix = "inline-attachment-renderer",
    name = ["use"],
    havingValue = "default",
    matchIfMissing = true,
  )
  fun defaultInlineAttachmentRenderer(
    @Qualifier("CachedTemplateDataFetcherFacadeImpl") templateDataFetcherFacade: TemplateDataFetcherFacade,
    objectMapper: ObjectMapper,
  ): InlineAttachmentRenderer = EmbeddedInlineAttachmentRenderer(
    templateDataFetcherFacade = templateDataFetcherFacade,
    objectMapper = objectMapper,
  )

  @Bean
  fun templateHelper(
    @Qualifier("CachedTemplateDataFetcherFacadeImpl") templateDataFetcherFacade: TemplateDataFetcherFacade,
    objectMapper: ObjectMapper,
    inlineAttachmentRenderer: InlineAttachmentRenderer,
  ) = TemplateHelpers(
    templateDataFetcherFacade = templateDataFetcherFacade,
    objectMapper = objectMapper,
    inlineAttachmentRenderer = inlineAttachmentRenderer,
  )

  @Bean
  fun fileBasedTemplateRenderService(
    templateHelpers: TemplateHelpers,
  ) = FileBasedTemplateRenderService(templateHelpers)

  @Bean("templateDataFetcherFacadeCacheBuilder")
  fun templateDataFetcherFacadeCacheBuilder(): Caffeine<Any, Any> = Caffeine
    .newBuilder()
    .maximumSize(maximumSize)
    .expireAfterWrite(expireAfter, TimeUnit.MINUTES)
}
