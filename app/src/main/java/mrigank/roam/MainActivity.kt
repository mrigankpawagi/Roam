package mrigank.roam

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mrigank.roam.data.Area
import mrigank.roam.databinding.ActivityMainBinding
import mrigank.roam.databinding.DialogRadiusBinding
import mrigank.roam.databinding.ItemAreaBinding
import mrigank.roam.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = AreaAdapter()

    private var pendingExportArea: Area? = null
    private var pendingExportWithProgress: Boolean = false

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        val area = pendingExportArea ?: return@registerForActivityResult
        viewModel.exportAreaToFile(area, pendingExportWithProgress, uri, contentResolver) { success ->
            Toast.makeText(
                this,
                if (success) getString(R.string.export_success) else getString(R.string.export_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.importAreaFromFile(uri, contentResolver) { success ->
            Toast.makeText(
                this,
                if (success) getString(R.string.import_success) else getString(R.string.import_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fab.setOnClickListener {
            startActivity(Intent(this, AreaSelectionActivity::class.java))
        }

        binding.fabImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        viewModel.allAreas.observe(this) { areas ->
            adapter.submitList(areas)
            binding.textEmpty.visibility =
                if (areas.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val eraserItem = menu.findItem(R.id.action_toggle_eraser)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        eraserItem.isChecked = prefs.getBoolean(PREF_ERASER_ENABLED, false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_eraser) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val newValue = !item.isChecked
            item.isChecked = newValue
            prefs.edit().putBoolean(PREF_ERASER_ENABLED, newValue).apply()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        // Refresh exploration percentages whenever this screen becomes visible again
        adapter.notifyDataSetChanged()
    }

    private fun showDeleteDialog(area: Area) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_area_title)
            .setMessage(getString(R.string.delete_area_message, area.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteArea(area) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRadiusDialog(area: Area) {
        val dialogBinding = DialogRadiusBinding.inflate(layoutInflater)
        val seekBar = dialogBinding.dialogSeekRadius
        val label = dialogBinding.dialogTextRadius

        val initialRadius = area.radiusMeters.toInt().coerceIn(1, 50)
        seekBar.progress = (initialRadius - 1).coerceIn(0, 49)
        label.text = getString(R.string.radius_value, initialRadius)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = progress + 1
                label.text = getString(R.string.radius_value, radius)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.set_radius)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newRadius = (seekBar.progress + 1).toDouble()
                viewModel.updateArea(area.copy(radiusMeters = newRadius))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showExportDialog(area: Area) {
        AlertDialog.Builder(this)
            .setTitle(R.string.export_area)
            .setItems(
                arrayOf(
                    getString(R.string.export_with_progress),
                    getString(R.string.export_without_progress)
                )
            ) { _, which ->
                pendingExportArea = area
                pendingExportWithProgress = (which == 0)
                val safeName = area.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                createDocumentLauncher.launch("roam_${safeName}.json")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class AreaAdapter : RecyclerView.Adapter<AreaAdapter.AreaViewHolder>() {

        private var areas: List<Area> = emptyList()

        fun submitList(newAreas: List<Area>) {
            areas = newAreas
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AreaViewHolder {
            val b = ItemAreaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AreaViewHolder(b)
        }

        override fun onBindViewHolder(holder: AreaViewHolder, position: Int) {
            holder.bind(areas[position])
        }

        override fun getItemCount() = areas.size

        inner class AreaViewHolder(private val b: ItemAreaBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(area: Area) {
                b.textAreaName.text = area.name
                b.textPercent.text = getString(R.string.percent_loading)
                b.progressBar.progress = 0

                viewModel.loadExploredPercent(area) { percent ->
                    b.textPercent.text = getString(R.string.percent_format, percent)
                    b.progressBar.progress = percent.toInt()
                }

                b.root.setOnClickListener {
                    val intent = Intent(this@MainActivity, ExplorationActivity::class.java).apply {
                        putExtra(ExplorationActivity.EXTRA_AREA_ID, area.id)
                    }
                    startActivity(intent)
                }

                b.buttonOverflow.setOnClickListener { anchor ->
                    val popup = PopupMenu(
                        ContextThemeWrapper(this@MainActivity, R.style.PopupMenuBlackText),
                        anchor
                    )
                    popup.menu.add(0, MENU_EDIT, 0, R.string.edit_area)
                    popup.menu.add(0, MENU_RADIUS, 1, R.string.set_radius)
                    popup.menu.add(0, MENU_EXPORT, 2, R.string.export_area)
                    popup.menu.add(0, MENU_DELETE, 3, R.string.delete)
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            MENU_EDIT -> {
                                startActivity(
                                    Intent(this@MainActivity, AreaSelectionActivity::class.java)
                                        .putExtra(AreaSelectionActivity.EXTRA_AREA_ID, area.id)
                                )
                                true
                            }
                            MENU_RADIUS -> {
                                showRadiusDialog(area)
                                true
                            }
                            MENU_EXPORT -> {
                                showExportDialog(area)
                                true
                            }
                            MENU_DELETE -> {
                                showDeleteDialog(area)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_RADIUS = 2
        private const val MENU_EXPORT = 3
        private const val MENU_DELETE = 4
        const val PREF_ERASER_ENABLED = "eraser_enabled"
    }
}
