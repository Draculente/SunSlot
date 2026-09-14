package de.sunslot.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)