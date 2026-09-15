package com.snp.bookstorebio.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class QrApproveRequest(
    val session_id: String,
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Int? = null,
    val token_type: String? = null,
    val scope: String? = null,
)

@Serializable
data class QrApproveResponse(
    val status: String,
)
