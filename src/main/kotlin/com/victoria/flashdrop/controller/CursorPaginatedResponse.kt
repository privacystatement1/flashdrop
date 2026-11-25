package com.victoria.flashdrop.controller

data class CursorPaginatedResponse<T>(
    val content: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursor: EventCursor?
)