package com.example.explore.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.explore.data.Area
import com.example.explore.data.ExploreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExploreRepository(application)

    val allAreas = repository.allAreas

    fun loadExploredPercent(area: Area, callback: (Float) -> Unit) {
        viewModelScope.launch {
            val percent = withContext(Dispatchers.IO) {
                repository.getExploredPercent(area)
            }
            callback(percent)
        }
    }

    fun deleteArea(area: Area) {
        viewModelScope.launch {
            repository.deleteArea(area)
        }
    }
}
