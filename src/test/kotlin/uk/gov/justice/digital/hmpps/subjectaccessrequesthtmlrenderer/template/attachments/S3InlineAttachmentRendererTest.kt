package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template.attachments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequest.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequest.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.InlineAttachment
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.documentstore.DocumentStore
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class S3InlineAttachmentRendererTest {

  @Captor
  private lateinit var inlineAttachmentFileCaptor: ArgumentCaptor<InlineAttachmentFile>

  private val objectMapper: ObjectMapper = jacksonObjectMapper()
  private val renderRequestInfo = RenderRequestInfo(UUID.randomUUID(), "service-one")
  private val context = Context.newBuilder(
    mapOf(
      "attachment" to mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 10,
      ),
    ),
  )
    .combine("render-request-info", renderRequestInfo)
    .build()

  private val templateDataFetcherFacade: TemplateDataFetcherFacade = mock()
  private val documentStore: DocumentStore = mock()
  private val handlebars: Handlebars = mock()
  private val options: Options = stubOptions(context)

  private val inlineAttachmentRenderer = S3InlineAttachmentRenderer(
    templateDataFetcherFacade,
    objectMapper,
    documentStore,
  )

  @Nested
  inner class InlineAttachmentContent {
    @Test
    fun `should return null when input is null`() {
      assertThat(inlineAttachmentRenderer.inlineAttachmentContent(null, options)).isNull()
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should throw exception when input is not inlineAttachment`() {
      val input: Any = listOf(1, 99, 87, 31, 19)

      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        inlineAttachmentRenderer.inlineAttachmentContent(input, options)
      }

      assertThat(actual.message).startsWith("Could not convert object to inline attachment:")
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return null if inlineAttachment url is empty`() {
      val input: Any = mapOf(
        "contentType" to "",
        "url" to "",
        "filesize" to null,
      )

      assertThat(inlineAttachmentRenderer.inlineAttachmentContent(input, options)).isNull()
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "application/json",
        "application/pdf",
        "text/html",
        "video/mp4",
      ],
    )
    fun `should throw exception when content type is invalid`(contentType: String) {
      val input: Any = mapOf(
        "contentType" to contentType,
        "url" to "http://url",
        "filesize" to null,
      )

      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        inlineAttachmentRenderer.inlineAttachmentContent(input, options)
      }

      assertThat(actual.message).startsWith("Inline attachment with content type $contentType not supported")
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should propagate exceptions from templateDataFetcher`() {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedCause = RuntimeException("templateDataFetcherFacade get attachment exception")

      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenThrow(expectedCause)

      val actual = assertThrows<RuntimeException> {
        inlineAttachmentRenderer.inlineAttachmentContent(input, options)
      }

      assertThat(actual).isEqualTo(expectedCause)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)
    }

    @Test
    fun `should throw exception when store document is unsuccessful`() = runTest {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedAttachmentData = "image 1234 data here".toByteArray()
      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenReturn(expectedAttachmentData)

      val expectedCause = SubjectAccessRequestException("store document error")
      whenever(documentStore.addInlineAttachment(any()))
        .thenThrow(expectedCause)

      val actual = assertThrows<SubjectAccessRequestException> {
        inlineAttachmentRenderer.inlineAttachmentContent(input, options)
      }

      assertThat(actual).isEqualTo(expectedCause)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)

      verify(documentStore, times(1))
        .addInlineAttachment(capture(inlineAttachmentFileCaptor))

      assertThat(inlineAttachmentFileCaptor.allValues).hasSize(1)
      val storedAttachmentFile = inlineAttachmentFileCaptor.allValues[0]
      assertThat(storedAttachmentFile.contentType).isEqualTo("image/png")
      assertThat(storedAttachmentFile.requestId).isEqualTo(renderRequestInfo.id)
      assertThat(storedAttachmentFile.serviceName).isEqualTo(renderRequestInfo.serviceName)
      assertThat(storedAttachmentFile.filename).endsWith(".png")
      assertThat(storedAttachmentFile.data).isEqualTo(expectedAttachmentData)
    }

    @Test
    fun `should return expected image src when attachment successfully processed`() = runTest {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedAttachmentData = "image 1234 data here".toByteArray()
      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenReturn(expectedAttachmentData)

      val actual = inlineAttachmentRenderer.inlineAttachmentContent(input, options)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)

      verify(documentStore, times(1))
        .addInlineAttachment(capture(inlineAttachmentFileCaptor))

      assertThat(inlineAttachmentFileCaptor.allValues).hasSize(1)
      val storedAttachmentFile = inlineAttachmentFileCaptor.allValues[0]
      assertThat(storedAttachmentFile.contentType).isEqualTo("image/png")
      assertThat(storedAttachmentFile.requestId).isEqualTo(renderRequestInfo.id)
      assertThat(storedAttachmentFile.serviceName).isEqualTo(renderRequestInfo.serviceName)
      assertThat(storedAttachmentFile.filename).endsWith(".png")
      assertThat(storedAttachmentFile.data).isEqualTo(expectedAttachmentData)
      assertThat(actual).matches("^https://sar-inline-attachments/${renderRequestInfo.id}/${renderRequestInfo.serviceName}/inline-attachments/[0-9a-zA-Z-]+\\.png$")
    }
  }

  @Nested
  inner class InlineAttachment {
    @Test
    fun `should return no data held when input is null`() {
      assertThat(inlineAttachmentRenderer.inlineAttachment(null, null, null, options))
        .isEqualTo("No Data Held")

      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should throw exception when input is not inlineAttachment`() {
      val input: Any = listOf(1, 99, 87, 31, 19)

      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        inlineAttachmentRenderer.inlineAttachment(input, null, null, options)
      }

      assertThat(actual.message).startsWith("Could not convert object to inline attachment:")
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return image with null src if inlineAttachment url is empty`() {
      val input: Any = mapOf(
        "contentType" to "",
        "url" to "",
        "filesize" to null,
      )

      assertThat(inlineAttachmentRenderer.inlineAttachment(input, null, null, options))
        .isEqualTo("""<img src="null" alt="Inline attachment" height="null" width="null"/>""")

      verifyNoInteractions(templateDataFetcherFacade)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "application/json",
        "application/pdf",
        "text/html",
        "video/mp4",
      ],
    )
    fun `should throw exception when content type is invalid`(contentType: String) {
      val input: Any = mapOf(
        "contentType" to contentType,
        "url" to "http://url",
        "filesize" to null,
      )

      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        inlineAttachmentRenderer.inlineAttachment(input, null, null, options)
      }

      assertThat(actual.message).startsWith("Inline attachment with content type $contentType not supported")
      verifyNoInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should propagate exceptions from templateDataFetcher`() {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedCause = RuntimeException("templateDataFetcherFacade get attachment exception")

      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenThrow(expectedCause)

      val actual = assertThrows<RuntimeException> {
        inlineAttachmentRenderer.inlineAttachment(input, null, null, options)
      }

      assertThat(actual).isEqualTo(expectedCause)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)
    }

    @Test
    fun `should throw exception when store document is unsuccessful`() = runTest {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedAttachmentData = "image 1234 data here".toByteArray()
      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenReturn(expectedAttachmentData)

      val expectedCause = SubjectAccessRequestException("store document error")
      whenever(documentStore.addInlineAttachment(any()))
        .thenThrow(expectedCause)

      val actual = assertThrows<SubjectAccessRequestException> {
        inlineAttachmentRenderer.inlineAttachment(input, null, null, options)
      }

      assertThat(actual).isEqualTo(expectedCause)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)

      verify(documentStore, times(1))
        .addInlineAttachment(capture(inlineAttachmentFileCaptor))

      assertThat(inlineAttachmentFileCaptor.allValues).hasSize(1)
      val storedAttachmentFile = inlineAttachmentFileCaptor.allValues[0]
      assertThat(storedAttachmentFile.contentType).isEqualTo("image/png")
      assertThat(storedAttachmentFile.requestId).isEqualTo(renderRequestInfo.id)
      assertThat(storedAttachmentFile.serviceName).isEqualTo(renderRequestInfo.serviceName)
      assertThat(storedAttachmentFile.filename).endsWith(".png")
      assertThat(storedAttachmentFile.data).isEqualTo(expectedAttachmentData)
    }

    @Test
    fun `should return expected image src when attachment successfully processed`() = runTest {
      val input: Any = mapOf(
        "contentType" to "image/png",
        "url" to "http://url",
        "filesize" to 100,
      )

      val inlineAttachment = InlineAttachment(
        contentType = "image/png",
        url = "http://url",
        filesize = 100,
      )

      val expectedAttachmentData = "image 1234 data here".toByteArray()
      whenever(templateDataFetcherFacade.getRenderableAttachment(inlineAttachment, renderRequestInfo))
        .thenReturn(expectedAttachmentData)

      val actual = inlineAttachmentRenderer.inlineAttachment(input, 101, 393, options)

      verify(templateDataFetcherFacade, times(1))
        .getRenderableAttachment(inlineAttachment, renderRequestInfo)

      verify(documentStore, times(1))
        .addInlineAttachment(capture(inlineAttachmentFileCaptor))

      assertThat(inlineAttachmentFileCaptor.allValues).hasSize(1)
      val storedAttachmentFile = inlineAttachmentFileCaptor.allValues[0]
      assertThat(storedAttachmentFile.contentType).isEqualTo("image/png")
      assertThat(storedAttachmentFile.requestId).isEqualTo(renderRequestInfo.id)
      assertThat(storedAttachmentFile.serviceName).isEqualTo(renderRequestInfo.serviceName)
      assertThat(storedAttachmentFile.filename).endsWith(".png")
      assertThat(storedAttachmentFile.data).isEqualTo(expectedAttachmentData)

      val htmlFragment = Jsoup.parseBodyFragment(actual)
      val imgElement = htmlFragment.selectFirst("img")

      assertThat(imgElement).isNotNull
      assertThat(imgElement!!.attr("src")).matches("^https://sar-inline-attachments/${renderRequestInfo.id}/${renderRequestInfo.serviceName}/inline-attachments/[0-9a-zA-Z-]+\\.png$")
      assertThat(imgElement.attr("alt")).isEqualTo("Inline attachment")
      assertThat(imgElement.attr("height")).isEqualTo("101")
      assertThat(imgElement.attr("width")).isEqualTo("393")
    }
  }

  private fun stubOptions(context: Context): Options = Options(
    handlebars,
    "",
    mock(),
    context,
    mock(),
    mock(),
    emptyArray(),
    emptyMap(),
    emptyList(),
  )
}
