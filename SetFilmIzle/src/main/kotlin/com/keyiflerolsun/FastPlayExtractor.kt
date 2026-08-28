// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class FastPlay : ExtractorApi() {
    override val name            = "FastPlay"
    override val mainUrl         = "https://fastplay.mom"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        val document = app.get(url, referer = extRef).text

        // window.STF_KOPRU = { src: "/manifests/.../master.txt?verify=..."
        val src = Regex("""STF_KOPRU\s*=\s*\{[\s\S]*?src:\s*[\"']([^\"']+)[\"']""").find(document)?.groupValues?.get(1)
            ?: Regex("""[\"']/manifests/[^\"']+master\.txt[^\"']*[\"']""").find(document)?.value?.trim('"', '\'')

        if (src.isNullOrBlank()) {
            // Eski format: /video/ID -> /manifests/ID/master.txt
            if (url.contains("/video/")) {
                val m3u = url.substringBefore("?").replace("/video/", "/manifests/") + "/master.txt"
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = m3u,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        quality = Qualities.Unknown.value
                    }
                )
                return
            }
            throw ErrorLoadingException("FastPlay: manifest bulunamadı")
        }

        val m3uLink = if (src.startsWith("http")) src else "$mainUrl$src"
        Log.d("Kekik_$name", "m3uLink » $m3uLink")

        // Altyazılar
        Regex("""[\"']file[\"']\s*:\s*[\"'](https?://[^\"']+\.vtt)[\"']""").findAll(document).forEach { m ->
            val subUrl = m.groupValues[1]
            val lang = when {
                subUrl.contains("tur", true) -> "Turkish"
                subUrl.contains("eng", true) -> "English"
                else -> "Subtitle"
            }
            subtitleCallback(SubtitleFile(lang, subUrl))
        }

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = m3uLink,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = url
                quality = Qualities.Unknown.value
            }
        )
    }
}
