package com.example.harry_android.domain.model

data class Book(
    val number: Int,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val description: String,
    val pages: Int,
    val coverUrl: String
)
