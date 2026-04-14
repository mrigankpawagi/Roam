package mrigank.roam

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import mrigank.roam.data.Area
import mrigank.roam.data.GridUtils
import mrigank.roam.databinding.ActivityExplorationBinding
import mrigank.roam.service.LocationTrackingService
import mrigank.roam.viewmodel.ExplorationViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

class ExplorationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExplorationBinding
    private val viewModel: ExplorationViewModel by viewModels()

    private var fogOverlay: FogOfWarOverlay? = null
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

            // 1. Fog-of-war overlay (drawn first, under boundary)
            val fog = FogOfWarOverlay(area, emptySet())
            fogOverlay = fog
            binding.mapView.overlays.add(fog)

            // 2. Area boundary polygon (drawn on top of fog so it stays visible)
            setupAreaOverlay(area)

            // 3. Current location dot (topmost overlay)
            val locOverlay = CurrentLocationOverlay()
            currentLocationOverlay = locOverlay
            binding.mapView.overlays.add(locOverlay)

            // Immediately seed with any cells that were already emitted before this
            // observer ran — this restores the overlay after the user navigates back.
            viewModel.exploredCells.value?.let { cells ->
                fog.cells = cells.map { Pair(it.cellRow, it.cellCol) }.toSet()
                binding.mapView.invalidate()
            }

            val bb = BoundingBox(area.maxLat, area.maxLng, area.minLat, area.minLng)
            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(bb, true, 80)
            }
        }

        viewModel.exploredCells.observe(this) { cells ->
            val cellSet = cells.map { Pair(it.cellRow, it.cellCol) }.toSet()
            fogOverlay?.cells = cellSet
            binding.mapView.invalidate()
            viewModel.updateExploredPercent()
        }

        viewModel.exploredPercent.observe(this) { percent ->
            binding.chipProgress.text = getString(R.string.percent_format, percent)
        }

        viewModel.isExploring.observe(this) { isExploring ->
            binding.buttonStartStop.text =
                if (isExploring) getString(R.string.stop) else getString(R.string.start)
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
        )
        binding.mapView.controller.setZoom(17.0)
    }

    private fun setupAreaOverlay(area: Area) {
        areaBoundingBoxOverlay?.let { binding.mapView.overlays.remove(it) }

        val polygons = GridUtils.parsePolygons(area.polygonsJson)
        if (polygons.isNotEmpty()) {
            // Draw each polygon boundary on top of the fog overlay
            for (ring in polygons) {
                val pts = ring.map { (lat, lng) -> GeoPoint(lat, lng) }.toMutableList()
                pts.add(pts[0]) // close the ring
                val polygon = Polygon(binding.mapView).apply {
                    points = pts
                    fillPaint.color = 0x00000000 // transparent fill — fog handles the area tint
                    outlinePaint.color = 0xFF2E7D32.toInt()
                    outlinePaint.strokeWidth = 4f
                }
                binding.mapView.overlays.add(polygon)
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
                fillPaint.color = 0x00000000 // transparent fill
                outlinePaint.color = 0xFF2E7D32.toInt()
                outlinePaint.strokeWidth = 4f
            }
            areaBoundingBoxOverlay = polygon
            binding.mapView.overlays.add(polygon)
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

    inner class FogOfWarOverlay(val area: Area, var cells: Set<Pair<Int, Int>>) : Overlay() {

        private val fogPaint = Paint().apply {
            color = 0xCC000000.toInt()
            style = Paint.Style.FILL
        }

        private val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.FILL
        }

        private var fogBitmap: Bitmap? = null
        private var bitmapCanvas: Canvas? = null

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val width = mapView.width
            val height = mapView.height
            if (width <= 0 || height <= 0) return

            val projection = mapView.projection

            // Recreate bitmap if the view size has changed
            if (fogBitmap == null || fogBitmap!!.width != width || fogBitmap!!.height != height) {
                fogBitmap?.recycle()
                fogBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmapCanvas = Canvas(fogBitmap!!)
            }

            val bmpCanvas = bitmapCanvas ?: return
            val bitmap = fogBitmap ?: return

            // Clear the bitmap to fully transparent
            bmpCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Fill the entire canvas with dark fog
            bmpCanvas.drawPaint(fogPaint)

            // Punch holes in the fog for explored cells
            if (cells.isNotEmpty()) {
                val rows = GridUtils.numRows(area)
                val cols = GridUtils.numCols(area)
                val latStep = (area.maxLat - area.minLat) / rows
                val lngStep = (area.maxLng - area.minLng) / cols

                for ((row, col) in cells) {
                    val cellMinLat = area.minLat + row * latStep
                    val cellMaxLat = cellMinLat + latStep
                    val cellMinLng = area.minLng + col * lngStep
                    val cellMaxLng = cellMinLng + lngStep

                    val tl = projection.toPixels(GeoPoint(cellMaxLat, cellMinLng), null)
                    val br = projection.toPixels(GeoPoint(cellMinLat, cellMaxLng), null)

                    bmpCanvas.drawRect(
                        tl.x.toFloat(), tl.y.toFloat(),
                        br.x.toFloat(), br.y.toFloat(),
                        clearPaint
                    )
                }
            }

            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
    }

    inner class CurrentLocationOverlay : Overlay() {

        private var lat = 0.0
        private var lng = 0.0

        private val fillPaint = Paint().apply {
            color = 0xFF2E7D32.toInt()
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
