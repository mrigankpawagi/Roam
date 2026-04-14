package mrigank.roam.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExploredCellDao {

    @Query("SELECT * FROM explored_cells WHERE areaId = :areaId")
    fun getExploredCellsForArea(areaId: Long): LiveData<List<ExploredCell>>

    @Query("SELECT * FROM explored_cells WHERE areaId = :areaId")
    suspend fun getExploredCellsForAreaSync(areaId: Long): List<ExploredCell>

    @Query("SELECT COUNT(*) FROM explored_cells WHERE areaId = :areaId")
    suspend fun getExploredCellCount(areaId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCell(cell: ExploredCell)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCells(cells: List<ExploredCell>)

    @Query("DELETE FROM explored_cells WHERE areaId = :areaId")
    suspend fun deleteAllForArea(areaId: Long)
}
