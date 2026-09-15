package com.snp.bookstore.data.source.remote

import com.snp.bookstore.BuildConfig
import com.snp.bookstore.data.source.remote.dto.BooksResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class BookstoreApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchBooks(accessToken: String): Result<BooksResponseDto> {
        val request = Request.Builder()
            .url("${BuildConfig.BOOKSTORE_API_BASE_URL}/api/books")
            .header("Authorization", "Bearer $accessToken")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Result.failure(IOException("HTTP ${response.code}: $body"))
                    } else {
                        Result.success(json.decodeFromString(BooksResponseDto.serializer(), body))
                    }
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
}
