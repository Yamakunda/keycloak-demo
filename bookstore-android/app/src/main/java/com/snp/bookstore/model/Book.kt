package com.snp.bookstore.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val genre: String? = null,
    val price: Double,
    val cover: String? = null,
)


@OptIn(InternalSerializationApi::class)
@Serializable
data class BooksResponse(
    val total: Int,
    val authenticatedAs: String? = null,
    val books: List<Book>,
)
