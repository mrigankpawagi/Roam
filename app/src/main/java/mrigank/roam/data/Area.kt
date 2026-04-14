package mrigank.roam.data

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
    /** JSON: [[{"lat":..,"lng":..},...], ...] — list of closed polygons defining the area. */
    val polygonsJson: String = "[]",
    /** Exploration radius in metres used by the tracking service for this area. */
    val radiusMeters: Double = 5.0,
    val cellSizeMeters: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis()
)
