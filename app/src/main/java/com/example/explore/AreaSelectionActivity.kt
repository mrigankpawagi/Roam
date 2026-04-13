package com.example.explore

import android.graphics.Canvas
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.explore.data.Area
import com.example.explore.data.ExploreRepository
import com.example.explore.databinding.ActivityAreaSelectionBinding
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

class AreaSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAreaSelectionBinding
    private var corner1: GeoPoint? = null
    private var corner2: GeoPoint? = null
    private var selectionPolygon: Polygon? = null
    private val repository by lazy { ExploreRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAreaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()

        binding.buttonSave.setOnClickListener { saveArea() }
        binding.buttonReset.setOnClickListener { resetSelection() }
    }

    private fun setupMap() {
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(51.5074, -0.1278))

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val gp = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                    handleDoubleTap(GeoPoint(gp.latitude, gp.longitude))
                    return true
                }
            }
        )

        val gestureOverlay = object : Overlay() {
            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                return gestureDetector.onTouchEvent(event)
            }

            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                // nothing to draw
            }
        }
        mapView.overlays.add(gestureOverlay)
    }

    private fun handleDoubleTap(point: GeoPoint) {
        if (corner1 == null) {
            corner1 = point
            binding.textInstructions.text = getString(R.string.instructions_second_corner)
        } else if (corner2 == null) {
            corner2 = point
            drawRectangle()
            binding.textInstructions.text = getString(R.string.instructions_ready)
        } else {
            // Reset and start over
            corner1 = point
            corner2 = null
            selectionPolygon?.let { binding.mapView.overlays.remove(it) }
            selectionPolygon = null
            binding.mapView.invalidate()
            binding.textInstructions.text = getString(R.string.instructions_second_corner)
        }
    }

    private fun drawRectangle() {
        val c1 = corner1 ?: return
        val c2 = corner2 ?: return

        selectionPolygon?.let { binding.mapView.overlays.remove(it) }

        val minLat = minOf(c1.latitude, c2.latitude)
        val maxLat = maxOf(c1.latitude, c2.latitude)
        val minLng = minOf(c1.longitude, c2.longitude)
        val maxLng = maxOf(c1.longitude, c2.longitude)

        val polygon = Polygon(binding.mapView).apply {
            points = listOf(
                GeoPoint(minLat, minLng),
                GeoPoint(maxLat, minLng),
                GeoPoint(maxLat, maxLng),
                GeoPoint(minLat, maxLng),
                GeoPoint(minLat, minLng)
            )
            fillPaint.color = 0x440000FF
            outlinePaint.color = 0xFF2196F3.toInt()
            outlinePaint.strokeWidth = 4f
        }

        selectionPolygon = polygon
        binding.mapView.overlays.add(polygon)
        binding.mapView.invalidate()
    }

    private fun resetSelection() {
        corner1 = null
        corner2 = null
        selectionPolygon?.let { binding.mapView.overlays.remove(it) }
        selectionPolygon = null
        binding.mapView.invalidate()
        binding.textInstructions.text = getString(R.string.instructions_select_area)
    }

    private fun saveArea() {
        val name = binding.editAreaName.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_enter_name), Toast.LENGTH_SHORT).show()
            return
        }

        val c1 = corner1
        val c2 = corner2
        if (c1 == null || c2 == null) {
            Toast.makeText(this, getString(R.string.error_select_area), Toast.LENGTH_SHORT).show()
            return
        }

        val minLat = minOf(c1.latitude, c2.latitude)
        val maxLat = maxOf(c1.latitude, c2.latitude)
        val minLng = minOf(c1.longitude, c2.longitude)
        val maxLng = maxOf(c1.longitude, c2.longitude)

        if (maxLat - minLat < 0.0001 || maxLng - minLng < 0.0001) {
            Toast.makeText(this, getString(R.string.error_area_too_small), Toast.LENGTH_SHORT).show()
            return
        }

        val area = Area(
            name = name,
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLng,
            maxLng = maxLng
        )

        lifecycleScope.launch {
            repository.insertArea(area)
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
}
