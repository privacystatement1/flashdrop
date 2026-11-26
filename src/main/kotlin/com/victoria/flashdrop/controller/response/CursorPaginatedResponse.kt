package com.victoria.flashdrop.controller.response

import com.victoria.flashdrop.controller.request.EventCursor

data class CursorPaginatedResponse<T>(
    val content: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursor: EventCursor?
)