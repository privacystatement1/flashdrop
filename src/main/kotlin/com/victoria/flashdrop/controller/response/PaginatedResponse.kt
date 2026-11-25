package com.victoria.flashdrop.controller.response

data class PaginatedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)