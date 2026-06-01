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

    suspend fun complete(apiKey: String, prompt: String): String {
        val req = GenerateContentRequest(listOf(Content(listOf(Part(prompt)))))
        return try {
            val res = service.generateContent(apiKey, req)
            res.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
        } catch(e: Exception) {
            "Error: ${e.message}"
        }
    }
}
