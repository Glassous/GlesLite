package com.glassous.gleslite

import java.io.Serializable

data class Favorite(
    val title: String,
    val url: String
) : Serializable