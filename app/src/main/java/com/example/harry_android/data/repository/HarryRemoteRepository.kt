package com.example.harry_android.data.repository

import com.example.harry_android.data.remote.api.HarryApiService
import com.example.harry_android.domain.model.Book
import com.example.harry_android.domain.repository.IRemoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HarryRemoteRepository @Inject constructor(private val apiService: HarryApiService) : IRemoteRepository{
    override suspend fun getBook(): Result<Book> = runCatching{
         apiService.getBook().toDomain()
    }
}