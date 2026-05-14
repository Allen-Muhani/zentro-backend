package com.grandtech.utils

/** Standard envelope returned by every API endpoint. */
data class ApiResponse<T>(
    /** HTTP status code (200 for success, 4xx/5xx for errors). */
    val status: Int,
    /** Short outcome description, e.g. "success" or an error message. */
    val message: String,
    /** Response data — may be a JSON object, array, primitive, or null. */
    val payload: T?
)
