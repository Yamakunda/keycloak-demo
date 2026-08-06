package com.snp.bookstorebio.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: Int,
    val title: String,
    val author: String,
    val genre: String? = null,
    val price: Double,
    val cover: String? = null,
)

@Serializable
data class BooksResponseDto(
    val total: Int,
    val authenticatedAs: String? = null,
    val books: List<BookDto>,
)
