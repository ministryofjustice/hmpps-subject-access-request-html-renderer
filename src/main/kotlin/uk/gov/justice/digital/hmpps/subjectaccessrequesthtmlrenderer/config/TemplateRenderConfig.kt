package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService

@Configuration
class TemplateRenderConfig {

  @Bean
  fun templateHelper(templateDataFetcherFacade: TemplateDataFetcherFacade, objectMapper: ObjectMapper) = TemplateHelpers(templateDataFetcherFacade, objectMapper)

  @Bean fun templateRenderService(templateHelpers: TemplateHelpers) = TemplateRenderService(templateHelpers)
}
