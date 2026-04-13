package in.mrigank.roam.data

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

    suspend fun updateArea(area: Area) = areaDao.updateArea(area)

    fun getExploredCellsForArea(areaId: Long): LiveData<List<ExploredCell>> =
        exploredCellDao.getExploredCellsForArea(areaId)

    suspend fun getExploredCellsForAreaSync(areaId: Long): List<ExploredCell> =
        exploredCellDao.getExploredCellsForAreaSync(areaId)

    suspend fun insertCell(cell: ExploredCell) = exploredCellDao.insertCell(cell)

    suspend fun insertCells(cells: List<ExploredCell>) = exploredCellDao.insertCells(cells)

    suspend fun deleteAllCellsForArea(areaId: Long) = exploredCellDao.deleteAllForArea(areaId)

    suspend fun getExploredPercent(area: Area): Float {
        val polygons = GridUtils.parsePolygons(area.polygonsJson)
        val rows = GridUtils.numRows(area)
        val cols = GridUtils.numCols(area)
        val totalCells: Long
        if (polygons.isEmpty()) {
            totalCells = rows.toLong() * cols.toLong()
        } else {
            totalCells = (0 until rows).sumOf { r ->
                (0 until cols).count { c ->
                    val (cLat, cLng) = GridUtils.cellCenterLatLng(area, r, c)
                    polygons.any { poly -> GridUtils.pointInPolygon(cLat, cLng, poly) }
                }.toLong()
            }
        }
        if (totalCells == 0L) return 0f
        val exploredCount = exploredCellDao.getExploredCellCount(area.id)
        return exploredCount.toFloat() / totalCells.toFloat() * 100f
    }
}
