package mrigank.roam.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class ExploreRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val areaDao = db.areaDao()
    private val exploredCellDao = db.exploredCellDao()

    val allAreas: LiveData<List<Area>> = areaDao.getAllAreas()

    suspend fun getAreaById(id: Long): Area? = areaDao.getAreaById(id)

    suspend fun insertArea(area: Area): Long = areaDao.insertArea(area)

    suspend fun deleteArea(area: Area) = areaDao.deleteArea(area)

    suspend fun updateArea(area: Area) = areaDao.updateArea(area)

    /** Atomically updates the area geometry and discards all previously explored cells. */
    suspend fun updateAreaAndClearExploredCells(area: Area) {
        db.withTransaction {
            areaDao.updateArea(area)
            exploredCellDao.deleteAllForArea(area.id)
        }
    }

    fun getExploredCellsForArea(areaId: Long): LiveData<List<ExploredCell>> =
        exploredCellDao.getExploredCellsForArea(areaId)

    suspend fun getExploredCellsForAreaSync(areaId: Long): List<ExploredCell> =
        exploredCellDao.getExploredCellsForAreaSync(areaId)

    suspend fun insertCell(cell: ExploredCell) = exploredCellDao.insertCell(cell)

    suspend fun insertCells(cells: List<ExploredCell>) = exploredCellDao.insertCells(cells)

    suspend fun deleteAllCellsForArea(areaId: Long) = exploredCellDao.deleteAllForArea(areaId)

    suspend fun deleteCell(areaId: Long, row: Int, col: Int) =
        exploredCellDao.deleteCell(areaId, row, col)

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
        return (exploredCount.toFloat() / totalCells.toFloat() * 100f).coerceIn(0f, 100f)
    }

    suspend fun buildExportJson(area: Area, includeProgress: Boolean): String {
        val areaObj = JSONObject().apply {
            put("name", area.name)
            put("minLat", area.minLat)
            put("maxLat", area.maxLat)
            put("minLng", area.minLng)
            put("maxLng", area.maxLng)
            put("polygonsJson", area.polygonsJson)
            put("radiusMeters", area.radiusMeters)
            put("cellSizeMeters", area.cellSizeMeters)
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("area", areaObj)
            if (includeProgress) {
                val cells = getExploredCellsForAreaSync(area.id)
                val arr = JSONArray()
                for (cell in cells) {
                    arr.put(JSONObject().apply {
                        put("row", cell.cellRow)
                        put("col", cell.cellCol)
                    })
                }
                put("exploredCells", arr)
            }
        }
        return root.toString(2)
    }

    suspend fun importFromJson(json: String): Boolean {
        return try {
            // Parse the entire payload before touching the database so that a malformed
            // file never leaves a partial area record behind.
            val root = JSONObject(json)
            val areaObj = root.getJSONObject("area")
            val area = Area(
                name = areaObj.getString("name"),
                minLat = areaObj.getDouble("minLat"),
                maxLat = areaObj.getDouble("maxLat"),
                minLng = areaObj.getDouble("minLng"),
                maxLng = areaObj.getDouble("maxLng"),
                polygonsJson = areaObj.optString("polygonsJson", "[]"),
                radiusMeters = areaObj.optDouble("radiusMeters", 5.0),
                cellSizeMeters = areaObj.optDouble("cellSizeMeters", 5.0)
            )
            val parsedCells: List<Pair<Int, Int>> = if (root.has("exploredCells")) {
                val cellsArr = root.getJSONArray("exploredCells")
                (0 until cellsArr.length()).map { i ->
                    val cell = cellsArr.getJSONObject(i)
                    Pair(cell.getInt("row"), cell.getInt("col"))
                }
            } else emptyList()
            val polygons = GridUtils.parsePolygons(area.polygonsJson)
            val rows = GridUtils.numRows(area)
            val cols = GridUtils.numCols(area)
            val sanitizedCells = parsedCells.distinct().filter { (row, col) ->
                if (row !in 0 until rows || col !in 0 until cols) {
                    false
                } else if (polygons.isEmpty()) {
                    true
                } else {
                    val (cLat, cLng) = GridUtils.cellCenterLatLng(area, row, col)
                    polygons.any { poly -> GridUtils.pointInPolygon(cLat, cLng, poly) }
                }
            }

            // Commit area and cells atomically.
            db.withTransaction {
                val areaId = insertArea(area)
                if (sanitizedCells.isNotEmpty()) {
                    insertCells(sanitizedCells.map { (row, col) ->
                        ExploredCell(areaId = areaId, cellRow = row, cellCol = col)
                    })
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
