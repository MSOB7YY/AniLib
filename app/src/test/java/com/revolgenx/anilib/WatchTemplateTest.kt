package com.revolgenx.anilib

import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchActionParam
import com.revolgenx.anilib.media.data.watch.WatchEncode
import com.revolgenx.anilib.media.data.watch.WatchTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTemplateTest {

    private val vars = mapOf(
        "media.title.romaji" to "Kaijuu 8-gou",
        "episode" to "5",
        "site.nekobt" to "s8861"
    )

    @Test
    fun rendersVariablesEncoded() {
        assertEquals(
            "Kaijuu+8-gou",
            WatchTemplate.render("{media.title.romaji}", vars, WatchEncode.QUERY)
        )
    }

    @Test
    fun appliesFilters() {
        assertEquals("05", WatchTemplate.render("{episode|pad2}", vars, WatchEncode.QUERY))
    }

    @Test
    fun coalescesToFirstNonBlank() {
        assertEquals(
            "s8861",
            WatchTemplate.render("{site.unknown ?? site.nekobt}", vars, WatchEncode.QUERY)
        )
        assertEquals(
            "latest",
            WatchTemplate.render("""{preset.sort ?? "latest"}""", vars, WatchEncode.QUERY)
        )
    }

    @Test
    fun rendersBlocksOnPresence() {
        assertEquals("", WatchTemplate.render("{!site.nekobt}x{/}", vars, WatchEncode.QUERY))
        assertEquals("05", WatchTemplate.render("{?episode}{episode|pad2}{/}", vars, WatchEncode.QUERY))
    }

    @Test
    fun nekoBtDropsTheTitleWhenTheSiteIdIsKnown() {
        val action = WatchAction(
            name = "nekoBT",
            baseUrl = "https://nekobt.to/search",
            params = listOf(
                WatchActionParam("sort_by", """{preset.sort ?? "latest"}"""),
                WatchActionParam("media_id", "{site.nekobt}"),
                WatchActionParam("episode_ids", "{site.nekobt.episode}"),
                WatchActionParam(
                    "query",
                    "{!site.nekobt}{media.title.romaji} {/}{!site.nekobt.episode}{?episode}{episode|pad2}{/}{/}"
                )
            )
        )

        assertEquals(
            "https://nekobt.to/search?sort_by=latest&media_id=s8861&query=05",
            action.buildUrl(vars)
        )

        assertEquals(
            "https://nekobt.to/search?sort_by=latest&query=Kaijuu+8-gou+05",
            action.buildUrl(vars - "site.nekobt")
        )
    }
}
