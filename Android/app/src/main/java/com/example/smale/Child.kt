package com.example.smale


import kotlinx.serialization.Serializable

@Serializable
data class Child(
    val id: String? = null,
    val nama: String,
    val usia: Int,
    val jenis_kelamin: String,
    val status: String
)