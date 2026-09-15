package com.snp.bookstore.domain.repository

import com.snp.bookstore.domain.model.Book

interface BookRepository {
    suspend fun getBooks(accessToken: String): Result<List<Book>>
}
