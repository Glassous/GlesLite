package com.glassous.gleslite

import com.google.gson.annotations.SerializedName

data class HistoryItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("timestamp") val timestamp: Long
)