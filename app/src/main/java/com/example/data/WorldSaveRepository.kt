package com.example.data

import com.example.engine.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorldSaveRepository(private val dao: WorldSaveDao) {
  val saveSummaries: Flow<List<WorldSaveSummary>> = dao.observeSummaries()

  suspend fun savePreparedSnapshot(
    slotId: String,
    slotTitle: String,
    year: Int,
    population: Int,
    nationCount: Int,
    cityCount: Int,
    mapSize: Int,
    compressedSnapshot: ByteArray,
  ) =
    withContext(Dispatchers.IO) {
      dao.upsertSave(
        WorldSaveEntity(
          slotId = slotId,
          slotTitle = slotTitle,
          year = year,
          population = population,
          nationCount = nationCount,
          cityCount = cityCount,
          mapSize = mapSize,
          savedAtMillis = System.currentTimeMillis(),
          compressedSnapshot = compressedSnapshot,
        )
      )
    }

  suspend fun loadWorld(slotId: String): WorldState? =
    withContext(Dispatchers.IO) {
      val entity = dao.getBySlotId(slotId) ?: return@withContext null
      WorldSerializer.decompressState(entity.compressedSnapshot)
    }

  suspend fun deleteSlot(slotId: String) =
    withContext(Dispatchers.IO) {
      dao.deleteSlot(slotId)
    }
}
