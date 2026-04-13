package com.example.explore.data

import android.content.Context
import androidx.lifecycle.LiveData

class ExploreRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val areaDao = db.areaDao()
    private val exploredCellDao = db.exploredCellDao()

    val allAreas: LiveData<List<Area>> = areaDao.getAllAreas()

    suspend fun getAreaById(id: Long): Area? = areaDao.getAreaById(id)

    suspend fun insertArea(area: Area): Long = areaDao.insertArea(area)

    suspend fun deleteArea(area: Area) = areaDao.deleteArea(area)

    fun getExploredCellsForArea(areaId: Long): LiveData<List<ExploredCell>> =
        exploredCellDao.getExploredCellsForArea(areaId)

    suspend fun getExploredCellsForAreaSync(areaId: Long): List<ExploredCell> =
        exploredCellDao.getExploredCellsForAreaSync(areaId)

    suspend fun insertCell(cell: ExploredCell) = exploredCellDao.insertCell(cell)

    suspend fun insertCells(cells: List<ExploredCell>) = exploredCellDao.insertCells(cells)

    suspend fun deleteAllCellsForArea(areaId: Long) = exploredCellDao.deleteAllForArea(areaId)

    suspend fun getExploredPercent(area: Area): Float {
        val totalCells = GridUtils.numRows(area).toLong() * GridUtils.numCols(area).toLong()
        if (totalCells == 0L) return 0f
        val exploredCount = exploredCellDao.getExploredCellCount(area.id)
        return exploredCount.toFloat() / totalCells.toFloat() * 100f
    }
}
