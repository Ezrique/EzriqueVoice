package dev.deftu.ezrique.voice.tts

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.ezrique.voice.EzriqueVoice
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object Weilnet {

    private const val BASE_URL = "https://tiktok-tts.weilnet.workers.dev"
    private val httpClient = OkHttpClient.Builder()
        .build()

    @Throws(IllegalStateException::class)
    fun getAudioData(
        text: String,
        voice: Voice
    ): String {
        val jsonBody = EzriqueVoice.GSON.toJson(JsonObject().apply {
            addProperty("text", text)
            addProperty("voice", voice.code)
        })

        val response = httpClient.newCall(
            Request.Builder()
                .url("$BASE_URL/api/generation")
                .post(jsonBody.toRequestBody())
                .addHeader("Content-Type", "application/json")
                .build()
        ).execute()

        return response.use {
            val body = response.body?.string() ?: throw IllegalStateException("Response body is null!")
            val json = JsonParser.parseString(body).asJsonObject

            if (json.get("success").asBoolean) {
                json.get("data").asString
            } else if (json.has("error")) {
                throw IllegalStateException(json.get("error").asString)
            } else {
                throw IllegalStateException("Unknown error!")
            }
        }
    }

}
