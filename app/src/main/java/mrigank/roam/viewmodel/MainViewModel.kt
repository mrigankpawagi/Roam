package mrigank.roam.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import mrigank.roam.data.Area
import mrigank.roam.data.ExploreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExploreRepository(application)

    val allAreas = repository.allAreas

    fun loadExploredPercent(area: Area, callback: (Float) -> Unit): Job {
        return viewModelScope.launch {
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

    fun updateArea(area: Area) {
        viewModelScope.launch {
            repository.updateArea(area)
        }
    }

    fun exportAreaToFile(
        area: Area,
        includeProgress: Boolean,
        uri: Uri,
        resolver: ContentResolver,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val json = repository.buildExportJson(area, includeProgress)
                    val stream = resolver.openOutputStream(uri)
                        ?: return@withContext false
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            onResult(success)
        }
    }

    fun importAreaFromFile(
        uri: Uri,
        resolver: ContentResolver,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val json = resolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: return@withContext false
                    repository.importFromJson(json)
                } catch (e: Exception) {
                    false
                }
            }
            onResult(success)
        }
    }
}
