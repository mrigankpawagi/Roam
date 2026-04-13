package com.example.explore.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.explore.data.Area
import com.example.explore.data.ExploredCell
import com.example.explore.data.ExploreRepository
import com.example.explore.data.GridUtils
import kotlinx.coroutines.launch

class ExplorationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExploreRepository(application)

    private val _areaId = MutableLiveData<Long>()

    private val _area = MutableLiveData<Area?>()
    val area: LiveData<Area?> = _area

    val exploredCells: LiveData<List<ExploredCell>> = _areaId.switchMap { id ->
        repository.getExploredCellsForArea(id)
    }

    private val _exploredPercent = MutableLiveData(0f)
    val exploredPercent: LiveData<Float> = _exploredPercent

    val isExploring = MutableLiveData(false)

    fun loadArea(areaId: Long) {
        _areaId.value = areaId
        viewModelScope.launch {
            val a = repository.getAreaById(areaId)
            _area.postValue(a)
        }
    }

    fun updateExploredPercent() {
        viewModelScope.launch {
            val currentArea = _area.value ?: return@launch
            val percent = repository.getExploredPercent(currentArea)
            _exploredPercent.postValue(percent)
        }
    }

    fun getTotalCells(): Int {
        val currentArea = _area.value ?: return 0
        return GridUtils.numRows(currentArea) * GridUtils.numCols(currentArea)
    }
}
