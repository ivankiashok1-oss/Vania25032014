package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldSaveDao {
  @Query(
    "SELECT slotId, slotTitle, year, population, nationCount, cityCount, mapSize, savedAtMillis FROM world_saves ORDER BY savedAtMillis DESC"
  )
  fun observeSummaries(): Flow<List<WorldSaveSummary>>

  @Query("SELECT * FROM world_saves WHERE slotId = :slotId LIMIT 1")
  suspend fun getBySlotId(slotId: String): WorldSaveEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSave(entity: WorldSaveEntity)

  @Query("DELETE FROM world_saves WHERE slotId = :slotId")
  suspend fun deleteSlot(slotId: String)
}
