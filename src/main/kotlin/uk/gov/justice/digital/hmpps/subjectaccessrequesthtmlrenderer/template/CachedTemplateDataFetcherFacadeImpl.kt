package uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.template

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.rendering.RenderRequestInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.InlineAttachment
import uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade
import java.util.Optional

@Service("CachedTemplateDataFetcherFacadeImpl")
class CachedTemplateDataFetcherFacadeImpl(
  @Qualifier("templateDataFetcherFacadeCacheBuilder") private val templateDataFetcherFacadeCacheBuilder: Caffeine<Any, Any>,
  @Qualifier("templateDataFetcherFacadeImpl") private val templateDataFetcherFacade: TemplateDataFetcherFacade
): TemplateDataFetcherFacade {

  private val prisonNameCache: LoadingCache<String, Optional<String>> = templateDataFetcherFacadeCacheBuilder.build { prisonId: String ->
    Optional.ofNullable(templateDataFetcherFacade.findPrisonNameByPrisonId(prisonId))
  }

  private val userDetailsCache = templateDataFetcherFacadeCacheBuilder
    .build { userId: String ->
      Optional.ofNullable(templateDataFetcherFacade.findUserLastNameByUsername(userId))
    }

  private val nomisLocationDetailsCache = templateDataFetcherFacadeCacheBuilder.build { nomisId: Int ->
    Optional.ofNullable(templateDataFetcherFacade.findLocationNameByNomisId(nomisId))
  }

  private val dpsLocationDetailsCache: LoadingCache<String, Optional<String>> = templateDataFetcherFacadeCacheBuilder.build { dpsId: String ->
    Optional.ofNullable(templateDataFetcherFacade.findLocationNameByDpsId(dpsId))
  }

  override fun findPrisonNameByPrisonId(prisonId: String): String? = prisonNameCache.get(prisonId).orElse(null)

  override fun findUserLastNameByUsername(userId: String): String? = userDetailsCache.get(userId).orElse(null)

  override fun findLocationNameByNomisId(nomisId: Int): String? = nomisLocationDetailsCache.get(nomisId).orElse(null)

  override fun findLocationNameByDpsId(dpsId: String): String? = dpsLocationDetailsCache.get(dpsId).orElse(null)

  override fun getRenderableAttachment(
    attachment: InlineAttachment,
    renderRequestInfo: RenderRequestInfo,
  ): ByteArray = templateDataFetcherFacade.getRenderableAttachment(attachment, renderRequestInfo)

  fun invalidateAll() {
    prisonNameCache.invalidateAll()
    userDetailsCache.invalidateAll()
    nomisLocationDetailsCache.invalidateAll()
    dpsLocationDetailsCache.invalidateAll()
  }
}