package com.example.explore.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "areas")
data class Area(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val cellSizeMeters: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis()
)
