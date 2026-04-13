package in.mrigank.roam

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import in.mrigank.roam.data.Area
import in.mrigank.roam.data.GridUtils
import in.mrigank.roam.databinding.ActivityExplorationBinding
import in.mrigank.roam.service.LocationTrackingService
import in.mrigank.roam.viewmodel.ExplorationViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

class ExplorationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExplorationBinding
    private val viewModel: ExplorationViewModel by viewModels()

    private var exploredCellsOverlay: ExploredCellsOverlay? = null
    private var currentLocationOverlay: CurrentLocationOverlay? = null
    private var areaBoundingBoxOverlay: Polygon? = null

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra(LocationTrackingService.EXTRA_LAT, 0.0) ?: return
            val lng = intent?.getDoubleExtra(LocationTrackingService.EXTRA_LNG, 0.0) ?: return
            currentLocationOverlay?.update(lat, lng)
            binding.mapView.invalidate()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startExploration()
        } else {
            Toast.makeText(
                this, getString(R.string.error_location_permission), Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExplorationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val areaId = intent.getLongExtra(EXTRA_AREA_ID, -1L)
        if (areaId == -1L) {
            finish()
            return
        }

        viewModel.loadArea(areaId)

        // Re-attach to the service if it was already running when we left the activity.
        // The volatile flag is a best-effort check; if the service stops concurrently the
        // receiver will simply never receive broadcasts and is cleaned up in onDestroy/stopExploration.
        if (LocationTrackingService.isRunning) {
            viewModel.isExploring.value = true
            LocalBroadcastManager.getInstance(this).registerReceiver(
                locationReceiver,
                IntentFilter(LocationTrackingService.ACTION_LOCATION_UPDATE)
            )
        }

        setupMap()

        binding.buttonStartStop.setOnClickListener {
            if (viewModel.isExploring.value == true) {
                stopExploration()
            } else {
                checkPermissionsAndStartExploration()
            }
        }

        viewModel.area.observe(this) { area ->
            area ?: return@observe
            setupAreaOverlay(area)

            val overlay = ExploredCellsOverlay(area, emptySet())
            exploredCellsOverlay = overlay
            binding.mapView.overlays.add(overlay)

            val locOverlay = CurrentLocationOverlay()
            currentLocationOverlay = locOverlay
            binding.mapView.overlays.add(locOverlay)

            // Immediately seed with any cells that were already emitted before this
            // observer ran — this restores the overlay after the user navigates back.
            viewModel.exploredCells.value?.let { cells ->
                overlay.cells = cells.map { Pair(it.cellRow, it.cellCol) }.toSet()
                binding.mapView.invalidate()
            }

            val bb = BoundingBox(area.maxLat, area.maxLng, area.minLat, area.minLng)
            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(bb, true, 80)
            }
        }

        viewModel.exploredCells.observe(this) { cells ->
            val cellSet = cells.map { Pair(it.cellRow, it.cellCol) }.toSet()
            exploredCellsOverlay?.cells = cellSet
            binding.mapView.invalidate()
            viewModel.updateExploredPercent()
        }

        viewModel.exploredPercent.observe(this) { percent ->
            binding.textProgress.text = getString(R.string.percent_format, percent)
        }

        viewModel.isExploring.observe(this) { isExploring ->
            binding.buttonStartStop.text =
                if (isExploring) getString(R.string.stop) else getString(R.string.start)
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(17.0)
    }

    private fun setupAreaOverlay(area: Area) {
        areaBoundingBoxOverlay?.let { binding.mapView.overlays.remove(it) }

        val polygons = GridUtils.parsePolygons(area.polygonsJson)
        if (polygons.isNotEmpty()) {
            // Draw each polygon
            for (ring in polygons) {
                val pts = ring.map { (lat, lng) -> GeoPoint(lat, lng) }.toMutableList()
                pts.add(pts[0]) // close the ring
                val polygon = Polygon(binding.mapView).apply {
                    points = pts
                    fillPaint.color = 0x110000FF
                    outlinePaint.color = 0xFF2196F3.toInt()
                    outlinePaint.strokeWidth = 4f
                }
                binding.mapView.overlays.add(0, polygon)
            }
        } else {
            // Fall back to bounding box rectangle
            val polygon = Polygon(binding.mapView).apply {
                points = listOf(
                    GeoPoint(area.minLat, area.minLng),
                    GeoPoint(area.maxLat, area.minLng),
                    GeoPoint(area.maxLat, area.maxLng),
                    GeoPoint(area.minLat, area.maxLng),
                    GeoPoint(area.minLat, area.minLng)
                )
                fillPaint.color = 0x110000FF
                outlinePaint.color = 0xFF2196F3.toInt()
                outlinePaint.strokeWidth = 4f
            }
            areaBoundingBoxOverlay = polygon
            binding.mapView.overlays.add(0, polygon)
        }
    }

    private fun checkPermissionsAndStartExploration() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startExploration()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startExploration() {
        val area = viewModel.area.value ?: return

        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_AREA_ID, area.id)
            putExtra(LocationTrackingService.EXTRA_RADIUS_METERS, area.radiusMeters)
            putExtra(LocationTrackingService.EXTRA_AREA_NAME, area.name)
        }
        startForegroundService(serviceIntent)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter(LocationTrackingService.ACTION_LOCATION_UPDATE)
        )

        viewModel.isExploring.value = true
    }

    private fun stopExploration() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(serviceIntent)

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
        } catch (e: IllegalArgumentException) {
            // receiver was not registered
        }

        viewModel.isExploring.value = false
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.isExploring.value == true) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
            } catch (e: IllegalArgumentException) {
                // receiver was not registered
            }
        }
    }

    inner class ExploredCellsOverlay(val area: Area, var cells: Set<Pair<Int, Int>>) : Overlay() {

        private val cellPaint = Paint().apply {
            color = 0x784CAF50
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow || cells.isEmpty()) return

            val rows = GridUtils.numRows(area)
            val cols = GridUtils.numCols(area)
            if (rows <= 0 || cols <= 0) return

            val latStep = (area.maxLat - area.minLat) / rows
            val lngStep = (area.maxLng - area.minLng) / cols
            val projection = mapView.projection

            for ((row, col) in cells) {
                val cellMinLat = area.minLat + row * latStep
                val cellMaxLat = cellMinLat + latStep
                val cellMinLng = area.minLng + col * lngStep
                val cellMaxLng = cellMinLng + lngStep

                val topLeft = projection.toPixels(GeoPoint(cellMaxLat, cellMinLng), null)
                val bottomRight = projection.toPixels(GeoPoint(cellMinLat, cellMaxLng), null)

                canvas.drawRect(
                    topLeft.x.toFloat(),
                    topLeft.y.toFloat(),
                    bottomRight.x.toFloat(),
                    bottomRight.y.toFloat(),
                    cellPaint
                )
            }
        }
    }

    inner class CurrentLocationOverlay : Overlay() {

        private var lat = 0.0
        private var lng = 0.0

        private val fillPaint = Paint().apply {
            color = 0xFF2196F3.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val strokePaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        fun update(lat: Double, lng: Double) {
            this.lat = lat
            this.lng = lng
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow || (lat == 0.0 && lng == 0.0)) return
            val point = mapView.projection.toPixels(GeoPoint(lat, lng), null)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 22f, strokePaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 16f, fillPaint)
        }
    }

    companion object {
        const val EXTRA_AREA_ID = "extra_area_id"
    }
}
