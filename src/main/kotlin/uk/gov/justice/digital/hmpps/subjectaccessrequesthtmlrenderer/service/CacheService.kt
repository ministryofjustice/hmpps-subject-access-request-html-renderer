package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.service

import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class CacheService {

  fun contains(key: String): Boolean = false // TODO implement later

  fun add(key: String, data: ByteArrayOutputStream?) {
    // TODO implement
  }
}
