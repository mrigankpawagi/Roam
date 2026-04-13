package com.example.explore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explore.data.Area
import com.example.explore.databinding.ActivityMainBinding
import com.example.explore.databinding.ItemAreaBinding
import com.example.explore.viewmodel.MainViewModel

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

                b.root.setOnLongClickListener {
                    viewModel.deleteArea(area)
                    true
                }
            }
        }
    }
}
