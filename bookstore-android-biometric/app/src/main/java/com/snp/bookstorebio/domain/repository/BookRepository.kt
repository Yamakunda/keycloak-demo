package com.snp.bookstorebio.domain.repository

import com.snp.bookstorebio.domain.model.Book

interface BookRepository {
    suspend fun getBooks(accessToken: String): Result<List<Book>>
}
