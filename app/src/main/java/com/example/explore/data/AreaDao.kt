package com.example.explore.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AreaDao {

    @Query("SELECT * FROM areas ORDER BY createdAt DESC")
    fun getAllAreas(): LiveData<List<Area>>

    @Query("SELECT * FROM areas WHERE id = :id")
    suspend fun getAreaById(id: Long): Area?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: Area): Long

    @Update
    suspend fun updateArea(area: Area)

    @Delete
    suspend fun deleteArea(area: Area)
}
