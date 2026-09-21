package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService
import java.util.concurrent.TimeUnit

@Configuration
class TemplateRenderConfig(
  @param:Value("\${template-helper-cache.maximum-size:10000}") val maximumSize: Long,
  @param:Value("\${template-helper-cache.expire-after-minutes:10}") val expireAfter: Long,
) {

  @Bean
  fun templateHelper(
    @Qualifier("CachedTemplateDataFetcherFacadeImpl") templateDataFetcherFacade: TemplateDataFetcherFacade,
    objectMapper: ObjectMapper,
  ) = TemplateHelpers(templateDataFetcherFacade, objectMapper)

  @Bean fun templateRenderService(templateHelpers: TemplateHelpers) = TemplateRenderService(templateHelpers)

  @Bean("templateDataFetcherFacadeCacheBuilder")
  fun templateDataFetcherFacadeCacheBuilder(): Caffeine<Any, Any> = Caffeine
    .newBuilder()
    .maximumSize(maximumSize)
    .expireAfterWrite(expireAfter, TimeUnit.MINUTES)
}
