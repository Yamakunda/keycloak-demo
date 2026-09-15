package com.snp.bookstore.domain.model

data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val genre: String? = null,
    val price: Double,
    val cover: String? = null,
)
