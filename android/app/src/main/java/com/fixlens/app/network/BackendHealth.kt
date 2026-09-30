package com.fixlens.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 1 wire contract. Only /health exists in Phase 1; later phases add
 * diagnosis/plan/target/verify responses validated the same way.
 */
@Serializable
data class BackendHealth(
    @SerialName("status") val status: String,
    @SerialName("version") val version: String? = null,
)
