package com.snp.bookstorebio.network

import com.snp.bookstorebio.BuildConfig
import com.snp.bookstorebio.model.BooksResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class BookstoreApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchBooks(accessToken: String): Result<BooksResponse> {
        val request = Request.Builder()
            .url("${BuildConfig.BOOKSTORE_API_BASE_URL}/api/books")
            .header("Authorization", "Bearer $accessToken")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                } else {
                    Result.success(json.decodeFromString(BooksResponse.serializer(), body))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
