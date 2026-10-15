package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.InlineAttachment
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import java.util.concurrent.TimeUnit

class CachedTemplateDataFetcherFacadeImplTest {

  private val cacheBuilder: Caffeine<Any, Any> = Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.SECONDS)
  private val templateDataFetcherFacade: TemplateDataFetcherFacade = mock()

  private val cachedTemplateDataFetcherFacadeImpl = CachedTemplateDataFetcherFacadeImpl(
    cacheBuilder,
    templateDataFetcherFacade,
  )

  @BeforeEach
  internal fun setUp() {
    cachedTemplateDataFetcherFacadeImpl.invalidateAll()

    whenever(templateDataFetcherFacade.findPrisonNameByPrisonId(prisonId = "MDI"))
      .thenReturn("HMP Moorland")

    whenever(templateDataFetcherFacade.findUserLastNameByUsername(userId = "12345"))
      .thenReturn("Smith")

    whenever(templateDataFetcherFacade.findLocationNameByNomisId(nomisId = 12345))
      .thenReturn("Cell 1")

    whenever(templateDataFetcherFacade.findLocationNameByDpsId(dpsId = "12345"))
      .thenReturn("Cell 2")
  }

  @Nested
  inner class FindPrisonNameByPrisonId {

    @Test
    fun `should call templateDataFetcherFacade when no cached value exists`() {
      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo("HMP Moorland")
      verify(templateDataFetcherFacade, times(1)).findPrisonNameByPrisonId("MDI")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return null when templateDataFetcherFacade returns null`() {
      whenever(templateDataFetcherFacade.findPrisonNameByPrisonId(prisonId = "MDI")).thenReturn(null)

      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findPrisonNameByPrisonId("MDI")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value exists in cache`() {
      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo("HMP Moorland")

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo("HMP Moorland")
      verify(templateDataFetcherFacade, times(1)).findPrisonNameByPrisonId("MDI")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value is null in cache`() {
      whenever(templateDataFetcherFacade.findPrisonNameByPrisonId(prisonId = "MDI")).thenReturn(null)

      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo(null)

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findPrisonNameByPrisonId(prisonId = "MDI")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findPrisonNameByPrisonId("MDI")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }
  }

  @Nested
  inner class FindUserLastNameByUsername {

    @Test
    fun `should call templateDataFetcherFacade when no cached value exists`() {
      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo("Smith")
      verify(templateDataFetcherFacade, times(1)).findUserLastNameByUsername("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return null when templateDataFetcherFacade returns null`() {
      whenever(templateDataFetcherFacade.findUserLastNameByUsername(userId = "12345")).thenReturn(null)

      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findUserLastNameByUsername("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value exists in cache`() {
      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo("Smith")

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo("Smith")
      verify(templateDataFetcherFacade, times(1)).findUserLastNameByUsername("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value is null in cache`() {
      whenever(templateDataFetcherFacade.findUserLastNameByUsername(userId = "12345")).thenReturn(null)

      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo(null)

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findUserLastNameByUsername(userId = "12345")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findUserLastNameByUsername("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }
  }

  @Nested
  inner class FindLocationNameByNomisId {

    @Test
    fun `should call templateDataFetcherFacade when no cached value exists`() {
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo("Cell 1")
      verify(templateDataFetcherFacade, times(1)).findLocationNameByNomisId(12345)
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return null when templateDataFetcherFacade returns null`() {
      whenever(templateDataFetcherFacade.findLocationNameByNomisId(nomisId = 12345)).thenReturn(null)

      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findLocationNameByNomisId(12345)
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value exists in cache`() {
      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo("Cell 1")

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo("Cell 1")
      verify(templateDataFetcherFacade, times(1)).findLocationNameByNomisId(nomisId = 12345)
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value is null in cache`() {
      whenever(templateDataFetcherFacade.findLocationNameByNomisId(nomisId = 12345)).thenReturn(null)

      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo(null)

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByNomisId(nomisId = 12345)).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findLocationNameByNomisId(12345)
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }
  }

  @Nested
  inner class FindLocationNameByDpsId {

    @Test
    fun `should call templateDataFetcherFacade when no cached value exists`() {
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo("Cell 2")
      verify(templateDataFetcherFacade, times(1)).findLocationNameByDpsId("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should return null when templateDataFetcherFacade returns null`() {
      whenever(templateDataFetcherFacade.findLocationNameByDpsId(dpsId = "12345")).thenReturn(null)

      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findLocationNameByDpsId("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value exists in cache`() {
      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo("Cell 2")

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo("Cell 2")
      verify(templateDataFetcherFacade, times(1)).findLocationNameByDpsId("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }

    @Test
    fun `should not call templateDataFetcherFacade when value is null in cache`() {
      whenever(templateDataFetcherFacade.findLocationNameByDpsId(dpsId = "12345")).thenReturn(null)

      // Prime the cache
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo(null)

      // call again and only 1 call should be made to the templateDataFetcherFacade
      assertThat(cachedTemplateDataFetcherFacadeImpl.findLocationNameByDpsId(dpsId = "12345")).isEqualTo(null)
      verify(templateDataFetcherFacade, times(1)).findLocationNameByDpsId("12345")
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }
  }

  @Nested
  inner class GetRenderableAttachment {

    private val attachment: InlineAttachment = mock()
    private val renderRequestInfo: RenderRequestInfo = mock()

    @Test
    fun `should delegate call to templateDataFetcherFacade`() {
      val expected = "[attachment data here]".toByteArray()
      whenever(templateDataFetcherFacade.getRenderableAttachment(attachment, renderRequestInfo))
        .thenReturn(expected)

      assertThat(templateDataFetcherFacade.getRenderableAttachment(attachment, renderRequestInfo)).isEqualTo(expected)
      verify(templateDataFetcherFacade, times(1)).getRenderableAttachment(attachment, renderRequestInfo)
      verifyNoMoreInteractions(templateDataFetcherFacade)
    }
  }
}
