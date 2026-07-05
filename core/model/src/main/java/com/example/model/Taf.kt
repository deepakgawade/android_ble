package com.example.model

data class Taf(
    val icaoId: String,
    val issueTime: String,
    val validFrom: String,
    val validTo: String,
    val rawTaf: String
)
