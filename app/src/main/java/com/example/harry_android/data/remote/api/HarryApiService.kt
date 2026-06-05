package com.example.harry_android.data.remote.api

import com.example.harry_android.data.remote.dto.BookDto
import retrofit2.http.GET

interface HarryApiService {

    @GET("en/books/random")
    suspend fun getBook(): BookDto

}