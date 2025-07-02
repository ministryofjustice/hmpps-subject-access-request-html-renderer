package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat

@Component
class ObjectMapperConfig {

  @Bean
  fun objectMapper(): ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerModule(KotlinModule.Builder().build())
    .setDateFormat(SimpleDateFormat("yyyy-MM-dd"))
}
