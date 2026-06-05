package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.Book

interface IRemoteRepository {
    /**
     * fetch a random book from harry api
     */
    suspend fun getBook() : Result<Book>
}