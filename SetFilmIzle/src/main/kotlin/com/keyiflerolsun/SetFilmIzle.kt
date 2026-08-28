// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONObject

class SetFilmIzle : MainAPI() {
    override var mainUrl              = "https://www.setfilmizle.ltd"
    override var name                 = "SetFilmIzle"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/tur/aile/"        to "Aile",
        "${mainUrl}/tur/aksiyon/"     to "Aksiyon",
        "${mainUrl}/tur/animasyon/"   to "Animasyon",
        "${mainUrl}/tur/belgesel/"    to "Belgesel",
        "${mainUrl}/tur/bilim-kurgu/" to "Bilim-Kurgu",
        "${mainUrl}/tur/biyografi/"   to "Biyografi",
        "${mainUrl}/tur/dini/"        to "Dini",
        "${mainUrl}/tur/dram/"        to "Dram",
        "${mainUrl}/tur/fantastik/"   to "Fantastik",
        "${mainUrl}/tur/genclik/"     to "Gençlik",
        "${mainUrl}/tur/gerilim/"     to "Gerilim",
        "${mainUrl}/tur/gizem/"       to "Gizem",
        "${mainUrl}/tur/komedi/"      to "Komedi",
        "${mainUrl}/tur/korku/"       to "Korku",
        "${mainUrl}/tur/macera/"      to "Macera",
        "${mainUrl}/tur/mini-dizi/"   to "Mini Dizi",
        "${mainUrl}/tur/muzik/"       to "Müzik",
        "${mainUrl}/tur/program/"     to "Program",
        "${mainUrl}/tur/romantik/"    to "Romantik",
        "${mainUrl}/tur/savas/"       to "Savaş",
        "${mainUrl}/tur/spor/"        to "Spor",
        "${mainUrl}/tur/suc/"         to "Suç",
        "${mainUrl}/tur/tarih/"       to "Tarih",
        "${mainUrl}/tur/western/"     to "Western",
        "${mainUrl}/film/"            to "Filmler",
        "${mainUrl}/dizi/"            to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(url).document
        val home = document.select("a.card-link").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        val title = this.selectFirst("h2.card-ad")?.text()?.trim()
            ?: this.selectFirst("img")?.attr("alt")?.trim()
            ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val isSeries = href.contains("/dizi/")
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("a.card-link").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1 .fbox-title-tx, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("div.poster img, div.poster-art img, .player-poster img")?.attr("src")
        )
        val description = document.selectFirst(".fbox-desc, .fbox-content p, .hcard-ozet")?.text()?.trim()
        val year = document.selectFirst(".fbox-date")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
            ?: Regex("\\b(19|20)\\d{2}\\b").find(document.selectFirst("h1")?.text() ?: "")?.value?.toIntOrNull()
        val tags = document.select(".fbox-genres a, .tumu").flatMap {
            it.text().split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }
        }.distinct()
        val actors = document.select(".fbox-cast a, dl.fbox-meta a").map { Actor(it.text().trim()) }

        // Dizi: bölüm linkleri
        val episodeLinks = document.select("a.fep")
        if (episodeLinks.isNotEmpty() || url.contains("/dizi/")) {
            val episodes = episodeLinks.mapNotNull { ep ->
                val epHref = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                val epName = ep.selectFirst(".fep-title, .fep-name")?.text()?.trim()
                    ?: epHref.substringAfterLast("/").replace("-", " ")
                val seasonNum = Regex("(\\d+)-sezon", RegexOption.IGNORE_CASE).find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                val epNum = Regex("(\\d+)-bolum", RegexOption.IGNORE_CASE).find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = epNum
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("STF", "data » $data")
        val document = app.get(data).document
        val html = document.html()

        val postId = document.selectFirst("#stfPlayer, .fplayer")?.attr("data-post-id")
            ?: Regex("""data-post-id=[\"'](\d+)[\"']""").find(html)?.groupValues?.get(1)
            ?: return false

        val nonce = Regex("""video:\s*[\"']([a-f0-9]+)[\"']""").find(html)?.groupValues?.get(1)
            ?: Regex("""nonce:\s*[\"']([a-f0-9]+)[\"']""").find(html)?.groupValues?.get(1)
            ?: ""

        val players = document.select("button.fsrc, .src-tab").map {
            Triple(
                it.attr("data-player-name").ifBlank { it.text().trim() },
                postId,
                it.attr("data-part-key")
            )
        }.ifEmpty {
            listOf(Triple("SetPlay", postId, ""))
        }

        for ((playerName, id, partKey) in players) {
            if (playerName.isBlank()) continue
            try {
                val body = app.post(
                    "${mainUrl}/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "get_video_url",
                        "nonce" to nonce,
                        "post_id" to id,
                        "player_name" to playerName,
                        "part_key" to partKey
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text

                Log.d("STF", "ajax » $body")
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) continue

                val dataObj = json.optJSONObject("data") ?: continue
                // Yeni format: data.stream.url
                val streamObj = dataObj.optJSONObject("stream")
                val streamUrl = streamObj?.optString("url")?.takeIf { it.isNotBlank() }
                    // Eski format: data.url
                    ?: dataObj.optString("url")?.takeIf { it.isNotBlank() }
                    ?: continue

                Log.d("STF", "stream » $streamUrl")
                loadExtractor(streamUrl, "${mainUrl}/", subtitleCallback, callback)
            } catch (e: Exception) {
                Log.d("STF", "player error: ${e.message}")
            }
        }

        return true
    }
}
