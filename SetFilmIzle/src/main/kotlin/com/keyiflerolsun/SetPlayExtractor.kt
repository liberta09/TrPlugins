// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class SetPlay : ExtractorApi() {
    override val name            = "SetPlay"
    override val mainUrl         = "https://setplay.shop"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        val page = app.get(url, referer = extRef).text

        // SPG.cerceve("b2", encoded, key) -> FastPlay iframe URL
        val encoded = Regex("""SPG\.cerceve\(\s*[\"']b2[\"']\s*,\s*[\"']([^\"']+)[\"']\s*,\s*[\"']([^\"']+)[\"']""").find(page)
        if (encoded != null) {
            val plain = xorDecode(encoded.groupValues[1], encoded.groupValues[2]).firstOrNull()
            if (!plain.isNullOrBlank()) {
                Log.d("Kekik_$name", "fastplay iframe » $plain")
                loadExtractor(plain, url, subtitleCallback, callback)
                return
            }
        }

        // KOKEN + jeton fallback
        val jeton = Regex("""jeton\s*=\s*[\"']([^\"']+)[\"']""").find(page)?.groupValues?.get(1)
        if (!jeton.isNullOrBlank()) {
            val fp = "https://fastplay.mom/stfplay.php?v=$jeton"
            loadExtractor(fp, url, subtitleCallback, callback)
            return
        }

        // Eski videoUrl formatı
        val videoUrl = Regex("""videoUrl[\"']?\s*[:=]\s*[\"']([^\"']+)[\"']""").find(page)?.groupValues?.get(1)
        val videoServer = Regex("""videoServer[\"']?\s*[:=]\s*[\"']([^\"']+)[\"']""").find(page)?.groupValues?.get(1)
        if (!videoUrl.isNullOrBlank() && !videoServer.isNullOrBlank()) {
            val m3u = "${mainUrl}${videoUrl.replace("\\", "")}?s=$videoServer"
            callback.invoke(
                newExtractorLink(source = name, name = name, url = m3u, type = ExtractorLinkType.M3U8) {
                    this.referer = url
                    quality = Qualities.Unknown.value
                }
            )
            return
        }

        throw ErrorLoadingException("SetPlay: kaynak bulunamadı")
    }

    private fun xorDecode(data: String, key: String): List<String> {
        return try {
            val r = Base64.decode(data, Base64.DEFAULT)
            val o = Base64.decode(key, Base64.DEFAULT)
            val out = ByteArray(r.size)
            for (i in r.indices) {
                out[i] = (r[i].toInt() xor o[i % o.size].toInt()).toByte()
            }
            String(out, Charsets.UTF_8).split("|")
        } catch (e: Exception) {
            emptyList()
        }
    }
}
