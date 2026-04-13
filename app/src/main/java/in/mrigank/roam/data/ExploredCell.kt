package in.mrigank.roam.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "explored_cells",
    primaryKeys = ["areaId", "cellRow", "cellCol"],
    foreignKeys = [ForeignKey(
        entity = Area::class,
        parentColumns = ["id"],
        childColumns = ["areaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("areaId")]
)
data class ExploredCell(
    val areaId: Long,
    val cellRow: Int,
    val cellCol: Int,
    val exploredAt: Long = System.currentTimeMillis()
)
