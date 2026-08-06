package com.snp.bookstorebio.data.repository

import com.snp.bookstorebio.data.source.remote.BookstoreApi
import com.snp.bookstorebio.domain.model.Book
import com.snp.bookstorebio.domain.repository.BookRepository
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val api: BookstoreApi
) : BookRepository {
    override suspend fun getBooks(accessToken: String): Result<List<Book>> {
        return api.fetchBooks(accessToken).map { response ->
            response.books.map { bookDto ->
                Book(
                    id = bookDto.id,
                    title = bookDto.title,
                    author = bookDto.author,
                    genre = bookDto.genre,
                    price = bookDto.price,
                    cover = bookDto.cover
                )
            }
        }
    }
}
