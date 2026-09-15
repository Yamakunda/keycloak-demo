package com.snp.bookstore.domain.usecase

import com.snp.bookstore.domain.model.Book
import com.snp.bookstore.domain.repository.BookRepository
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(accessToken: String): Result<List<Book>> {
        return repository.getBooks(accessToken)
    }
}
