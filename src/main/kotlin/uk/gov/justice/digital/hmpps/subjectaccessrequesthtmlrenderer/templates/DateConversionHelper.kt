package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.templates

import org.springframework.stereotype.Service
import java.text.ParsePosition
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.collections.ArrayList

// Copied directly from worker.
@Service
class DateConversionHelper {
  companion object {
    var dateConversions: ArrayList<DateConversion> = arrayListOf(
      // "2024-05-01"
      DateConversion(
        "^\\d{4}-\\d{2}-\\d{2}$".toRegex(),
        "yyyy-MM-dd",
        "dd MMMM yyyy",
      ),
      // "01/05/2024"
      DateConversion(
        "^\\d{2}/\\d{2}/\\d{4}$".toRegex(),
        "dd/MM/yyyy",
        "dd MMMM yyyy",
      ),
      // "2024-05-01T12:34:56[.1|12|123|1234|12345|123456|1234567|12345678|123456789][Z][+00:00]"
      DateConversion(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z?(\\+\\d{2}:\\d{2})?$".toRegex(),
        "yyyy-MM-dd'T'HH:mm:ss",
        "dd MMMM yyyy, h:mm:ss a",
      ),
      // "01/05/2024 12:34
      DateConversion(
        "^\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}$".toRegex(),
        "dd/MM/yyyy HH:mm",
        "dd MMMM yyyy, h:mm a",
      ),
      // "01/05/2024 12:34:56
      DateConversion(
        "^\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}$".toRegex(),
        "dd/MM/yyyy HH:mm:ss",
        "dd MMMM yyyy, h:mm:ss a",
      ),
      // "2024-05-01 12:34:56[.1|12|123|1234|12345|123456][+00]"
      DateConversion(
        "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})(\\+\\d{2})?$".toRegex(),
        "yyyy-MM-dd HH:mm:ss",
        "dd MMMM yyyy, h:mm:ss a",
      ),
      // "2024-05-01T12:34[Z]"
      DateConversion(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z?$".toRegex(),
        "yyyy-MM-dd'T'HH:mm",
        "dd MMMM yyyy, h:mm a",
      ),

      // 2025-04-03T14:34:41+0100
      DateConversion(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+\\d{2}\\d{2}?$".toRegex(),
        "yyyy-MM-dd'T'HH:mm",
        "dd MMMM yyyy, h:mm a",
      ),

    )
  }

  fun convertDates(input: String): String {
    dateConversions.forEach { dateConversion ->
      if (dateConversion.matcher.matches(input)) {
        val parseFormatter = DateTimeFormatter.ofPattern(dateConversion.parseFormat).withLocale(Locale.UK)
        val outputFormatter = DateTimeFormatter.ofPattern(dateConversion.outputFormat).withLocale(Locale.UK)
        val parsedDate = parseFormatter.parse(input, ParsePosition(0))
        return outputFormatter.format(parsedDate)
      }
    }
    return input
  }
}
