package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val REPOSITORIES_KEY = "REPOSITORIES_KEY"

@Serializable
data class RepositoryData(
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("url") @SerialName("url") val url: String,
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String? = null
)
