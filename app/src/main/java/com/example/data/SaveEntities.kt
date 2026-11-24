package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_saves")
data class WorldSaveEntity(
  @PrimaryKey val slotId: String, // "autosave", "slot_1", "slot_2", "slot_3"
  val slotTitle: String,
  val year: Int,
  val population: Int,
  val nationCount: Int,
  val cityCount: Int,
  val mapSize: Int,
  val savedAtMillis: Long,
  val compressedSnapshot: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorldSaveEntity) return false
    return slotId == other.slotId && savedAtMillis == other.savedAtMillis
  }

  override fun hashCode(): Int = slotId.hashCode() * 31 + savedAtMillis.hashCode()
}

data class WorldSaveSummary(
  val slotId: String,
  val slotTitle: String,
  val year: Int,
  val population: Int,
  val nationCount: Int,
  val cityCount: Int,
  val mapSize: Int,
  val savedAtMillis: Long,
)
