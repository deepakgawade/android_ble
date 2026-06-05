package com.example.harry_android.data.remote.dto
import android.annotation.SuppressLint
import com.example.harry_android.domain.model.Book
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BookDto(
    @SerialName("number")        val number: Int,
    @SerialName("title")         val title: String,
    @SerialName("originalTitle") val originalTitle: String,
    @SerialName("releaseDate")   val releaseDate: String,
    @SerialName("description")   val description: String,
    @SerialName("pages")         val pages: Int,
    @SerialName("cover")      val coverUrl: String
) {
    fun toDomain() = Book(
        number = number,
        title = title,
        originalTitle = originalTitle,
        releaseDate = releaseDate,
        description = description,
        pages = pages,
        coverUrl = coverUrl
    )
}

