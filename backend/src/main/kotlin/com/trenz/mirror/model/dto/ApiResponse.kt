package com.trenz.mirror.model.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class ApiResponse<out T> {
    @Serializable
    data class Success<T>(val data: T) : ApiResponse<T>()

    @Serializable
    data class Error(val code: String, val message: String) : ApiResponse<Nothing>()
}
