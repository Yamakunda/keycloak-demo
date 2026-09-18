package com.snp.bookstorebio.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class QrApproveRequest(
    val session_id: String,
    val access_token: String,
)

@Serializable
data class QrApproveResponse(
    val status: String,
)
