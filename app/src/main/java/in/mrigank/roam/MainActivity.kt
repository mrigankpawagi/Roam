package in.mrigank.roam

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import in.mrigank.roam.data.Area
import in.mrigank.roam.databinding.ActivityMainBinding
import in.mrigank.roam.databinding.DialogRadiusBinding
import in.mrigank.roam.databinding.ItemAreaBinding
import in.mrigank.roam.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = AreaAdapter()

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

        viewModel.allAreas.observe(this) { areas ->
            adapter.submitList(areas)
            binding.textEmpty.visibility =
                if (areas.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
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
                    val popup = PopupMenu(this@MainActivity, anchor)
                    popup.menu.add(0, MENU_EDIT, 0, R.string.edit_area)
                    popup.menu.add(0, MENU_RADIUS, 1, R.string.set_radius)
                    popup.menu.add(0, MENU_DELETE, 2, R.string.delete)
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
        private const val MENU_DELETE = 3
    }
}
