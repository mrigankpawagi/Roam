package in.mrigank.roam

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import in.mrigank.roam.data.Area
import in.mrigank.roam.data.ExploreRepository
import in.mrigank.roam.data.GridUtils
import in.mrigank.roam.databinding.ActivityAreaSelectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AreaSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAreaSelectionBinding
    private val repository by lazy { ExploreRepository(this) }

    // Edit mode: if set, we update this area instead of creating a new one
    private var editAreaId: Long = -1L

    // All completed closed polygons (list of vertex lists)
    private val completedPolygons = mutableListOf<MutableList<GeoPoint>>()

    // Vertices of the polygon currently being drawn
    private val currentVertices = mutableListOf<GeoPoint>()

    // Overlays for the polygons
    private val completedOverlays = mutableListOf<Polygon>()
    private var activePolylineOverlay: Polyline? = null
    private val vertexPaint = Paint().apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val vertexStrokePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAreaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar setup with up/back navigation
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editAreaId = intent.getLongExtra(EXTRA_AREA_ID, -1L)

        setupMap()
        setupButtons()
        setupSearch()

        if (editAreaId != -1L) {
            supportActionBar?.title = getString(R.string.edit_area)
            loadExistingArea(editAreaId)
        } else {
            supportActionBar?.title = getString(R.string.add_area)
        }

        updateInstructions()
        updateButtonStates()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadExistingArea(areaId: Long) {
        lifecycleScope.launch {
            val area = withContext(Dispatchers.IO) { repository.getAreaById(areaId) } ?: return@launch
            binding.editAreaName.setText(area.name)

            val polygons = GridUtils.parsePolygons(area.polygonsJson)
            for (ring in polygons) {
                val pts = ring.map { (lat, lng) -> GeoPoint(lat, lng) }.toMutableList()
                addCompletedPolygon(pts)
            }
            updateInstructions()
            updateButtonStates()

            if (completedPolygons.isNotEmpty()) {
                val all = completedPolygons.flatten()
                val minLat = all.minOf { it.latitude }
                val maxLat = all.maxOf { it.latitude }
                val minLng = all.minOf { it.longitude }
                val maxLng = all.maxOf { it.longitude }
                binding.mapView.post {
                    binding.mapView.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox(maxLat, maxLng, minLat, minLng),
                        true, 80
                    )
                }
            }
        }
    }

    private fun setupMap() {
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(51.5074, -0.1278))

        val tapOverlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val gp = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                addVertex(GeoPoint(gp.latitude, gp.longitude))
                return true
            }

            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                if (shadow) return
                // Draw vertex dots for current in-progress polygon
                for (vertex in currentVertices) {
                    val pt = mapView.projection.toPixels(vertex, null)
                    canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 12f, vertexStrokePaint)
                    canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 9f, vertexPaint)
                }
            }
        }
        mapView.overlays.add(tapOverlay)
    }

    private fun addVertex(point: GeoPoint) {
        currentVertices.add(point)
        refreshActivePolyline()
        updateInstructions()
        updateButtonStates()
    }

    private fun refreshActivePolyline() {
        val mapView = binding.mapView
        activePolylineOverlay?.let { mapView.overlays.remove(it) }
        if (currentVertices.size >= 2) {
            val line = Polyline(mapView).apply {
                setPoints(currentVertices.toList())
                outlinePaint.color = 0xFF2196F3.toInt()
                outlinePaint.strokeWidth = 4f
            }
            activePolylineOverlay = line
            mapView.overlays.add(line)
        } else {
            activePolylineOverlay = null
        }
        mapView.invalidate()
    }

    private fun closeCurrentPolygon() {
        if (currentVertices.size < 3) return
        val pts = currentVertices.toMutableList()
        addCompletedPolygon(pts)
        currentVertices.clear()
        refreshActivePolyline()
        updateInstructions()
        updateButtonStates()
    }

    private fun addCompletedPolygon(pts: MutableList<GeoPoint>) {
        completedPolygons.add(pts)
        val polygon = Polygon(binding.mapView).apply {
            val closed = pts.toMutableList().also { it.add(pts[0]) }
            points = closed
            fillPaint.color = 0x440000FF
            outlinePaint.color = 0xFF2196F3.toInt()
            outlinePaint.strokeWidth = 4f
        }
        completedOverlays.add(polygon)
        binding.mapView.overlays.add(0, polygon)
        binding.mapView.invalidate()
    }

    private fun setupButtons() {
        binding.buttonUndo.setOnClickListener {
            if (currentVertices.isNotEmpty()) {
                currentVertices.removeAt(currentVertices.size - 1)
                refreshActivePolyline()
            } else if (completedPolygons.isNotEmpty()) {
                // Undo the last closed polygon
                completedPolygons.removeAt(completedPolygons.size - 1)
                val overlay = completedOverlays.removeAt(completedOverlays.size - 1)
                binding.mapView.overlays.remove(overlay)
                binding.mapView.invalidate()
            }
            updateInstructions()
            updateButtonStates()
        }

        binding.buttonClosePolygon.setOnClickListener {
            closeCurrentPolygon()
        }

        binding.buttonNewPolygon.setOnClickListener {
            // Start a new disjoint polygon (current polygon already closed)
            updateInstructions()
            updateButtonStates()
        }

        binding.buttonReset.setOnClickListener { resetAll() }
        binding.buttonSave.setOnClickListener { saveArea() }
    }

    private fun resetAll() {
        currentVertices.clear()
        completedPolygons.clear()
        completedOverlays.forEach { binding.mapView.overlays.remove(it) }
        completedOverlays.clear()
        activePolylineOverlay?.let { binding.mapView.overlays.remove(it) }
        activePolylineOverlay = null
        binding.mapView.invalidate()
        updateInstructions()
        updateButtonStates()
    }

    private fun updateInstructions() {
        binding.textInstructions.text = when {
            completedPolygons.isNotEmpty() && currentVertices.isEmpty() ->
                getString(R.string.instructions_polygon_closed)
            currentVertices.size >= 3 ->
                getString(R.string.instructions_close_ready)
            currentVertices.size >= 1 ->
                getString(R.string.instructions_tap_more)
            else ->
                getString(R.string.instructions_tap_vertex)
        }
    }

    private fun updateButtonStates() {
        val inProgress = currentVertices.isNotEmpty()
        val canClose = currentVertices.size >= 3
        val hasCompleted = completedPolygons.isNotEmpty()

        binding.buttonUndo.isEnabled = inProgress || hasCompleted
        binding.buttonClosePolygon.isEnabled = canClose
        binding.buttonNewPolygon.isEnabled = hasCompleted && !inProgress
    }

    private fun setupSearch() {
        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
        binding.buttonSearch.setOnClickListener { performSearch() }
    }

    private fun performSearch() {
        val query = binding.editSearch.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return

        // Dismiss keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)

        lifecycleScope.launch {
            val results = searchNominatim(query)
            if (results.isEmpty()) {
                Toast.makeText(this@AreaSelectionActivity, "No results found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (results.size == 1) {
                moveMapTo(results[0])
            } else {
                val items = results.map { it.displayName }.toTypedArray()
                AlertDialog.Builder(this@AreaSelectionActivity)
                    .setTitle(R.string.search)
                    .setItems(items) { _, which -> moveMapTo(results[which]) }
                    .show()
            }
        }
    }

    private fun moveMapTo(result: NominatimResult) {
        binding.mapView.controller.animateTo(GeoPoint(result.lat, result.lng))
        binding.mapView.controller.setZoom(16.0)
    }

    private suspend fun searchNominatim(query: String): List<NominatimResult> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "ExploreApp/1.0 (android)")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val text = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(text)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    NominatimResult(
                        displayName = obj.getString("display_name"),
                        lat = obj.getDouble("lat"),
                        lng = obj.getDouble("lon")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun saveArea() {
        val name = binding.editAreaName.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_enter_name), Toast.LENGTH_SHORT).show()
            return
        }

        // Include any open (in-progress) polygon if it has >= 3 vertices
        val allPolygons = completedPolygons.map { pts ->
            pts.map { Pair(it.latitude, it.longitude) }
        }.toMutableList()
        if (currentVertices.size >= 3) {
            allPolygons.add(currentVertices.map { Pair(it.latitude, it.longitude) })
        }

        if (allPolygons.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_polygon), Toast.LENGTH_SHORT).show()
            return
        }

        val bb = GridUtils.boundingBox(allPolygons) ?: run {
            Toast.makeText(this, getString(R.string.error_area_too_small), Toast.LENGTH_SHORT).show()
            return
        }

        if (bb.maxLat - bb.minLat < 0.0001 || bb.maxLng - bb.minLng < 0.0001) {
            Toast.makeText(this, getString(R.string.error_area_too_small), Toast.LENGTH_SHORT).show()
            return
        }

        val polygonsJson = GridUtils.serializePolygons(allPolygons)

        lifecycleScope.launch {
            if (editAreaId != -1L) {
                val existing = withContext(Dispatchers.IO) { repository.getAreaById(editAreaId) }
                    ?: return@launch
                val updated = existing.copy(
                    name = name,
                    minLat = bb.minLat,
                    maxLat = bb.maxLat,
                    minLng = bb.minLng,
                    maxLng = bb.maxLng,
                    polygonsJson = polygonsJson
                )
                withContext(Dispatchers.IO) { repository.updateArea(updated) }
            } else {
                val area = Area(
                    name = name,
                    minLat = bb.minLat,
                    maxLat = bb.maxLat,
                    minLng = bb.minLng,
                    maxLng = bb.maxLng,
                    polygonsJson = polygonsJson
                )
                withContext(Dispatchers.IO) { repository.insertArea(area) }
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    data class NominatimResult(val displayName: String, val lat: Double, val lng: Double)

    companion object {
        const val EXTRA_AREA_ID = "extra_area_id"
    }
}
