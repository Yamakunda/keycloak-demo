package com.snp.bookstorebio.data.source.remote

import com.snp.bookstorebio.BuildConfig
import com.snp.bookstorebio.data.source.remote.dto.BooksResponseDto
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

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Result.failure(java.io.IOException("HTTP ${response.code}: $body"))
                    } else {
                        Result.success(json.decodeFromString(BooksResponseDto.serializer(), body))
                    }
                }
            } catch (e: java.io.IOException) {
                Result.failure(e)
            }
        }
    }
}
