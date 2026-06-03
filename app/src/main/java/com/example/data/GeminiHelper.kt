package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import okhttp3.OkHttpClient

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(val contents: List<Content>)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class Part(val text: String)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

@JsonClass(generateAdapter = true)
data class Candidate(val content: Content?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiHelper {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        
    private val service = retrofit.create(GeminiApiService::class.java)

    suspend fun complete(apiKeysStr: String, prompt: String): String {
        val apiKeys = apiKeysStr.split(Regex("[,\n\\s]+")).filter { it.isNotBlank() }.take(10)
        if (apiKeys.isEmpty()) {
            return "Error: No API keys provided."
        }
        val req = GenerateContentRequest(listOf(Content(listOf(Part(prompt)))))
        var lastError = "Unknown error"
        
        for (apiKey in apiKeys) {
            try {
                val res = service.generateContent(apiKey, req)
                val text = res.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) return text
            } catch(e: Exception) {
                lastError = e.message ?: "Unknown error"
                continue
            }
        }
        return "Error: $lastError (Tried ${apiKeys.size} keys)"
    }
}
