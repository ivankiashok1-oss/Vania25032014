package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Procedural retro 16-bit audio synthesizer using AudioTrack.
 * Supports configurable sound volume, siren volume, interception SFX, and ambient music toggle.
 */
class SirenSoundEngine {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val audioSemaphore = Semaphore(4)
  private var sirenJob: Job? = null
  private var musicJob: Job? = null
  @Volatile private var lastExplosionMs: Long = 0L
  @Volatile private var lastInterceptMs: Long = 0L

  @Volatile var enabled: Boolean = true
  @Volatile var soundVolume: Float = 0.8f
  @Volatile var sirenVolume: Float = 0.85f
  @Volatile var musicEnabled: Boolean = false
    set(value) {
      field = value
      if (value && enabled) startAmbientMusic() else stopAmbientMusic()
    }

  fun updateVolumes(soundVol: Float, sirenVol: Float, music: Boolean) {
    soundVolume = soundVol.coerceIn(0f, 1f)
    sirenVolume = sirenVol.coerceIn(0f, 1f)
    musicEnabled = music
  }

  fun playAirRaidSiren() {
    if (!enabled || sirenVolume <= 0.02f) return
    sirenJob?.cancel()
    sirenJob =
      scope.launch {
        audioSemaphore.withPermit {
          playToneBuffer(durationMs = 1350, mode = 0, gain = sirenVolume)
        }
      }
  }

  fun playExplosion() {
    if (!enabled || soundVolume <= 0.02f) return
    val now = System.currentTimeMillis()
    if (now - lastExplosionMs < 65L) return
    lastExplosionMs = now
    scope.launch {
      if (!audioSemaphore.tryAcquire()) return@launch
      try {
        playToneBuffer(durationMs = 300, mode = 1, gain = soundVolume)
      } finally {
        audioSemaphore.release()
      }
    }
  }

  fun playIntercept() {
    if (!enabled || soundVolume <= 0.02f) return
    val now = System.currentTimeMillis()
    if (now - lastInterceptMs < 65L) return
    lastInterceptMs = now
    scope.launch {
      if (!audioSemaphore.tryAcquire()) return@launch
      try {
        playToneBuffer(durationMs = 200, mode = 2, gain = soundVolume)
      } finally {
        audioSemaphore.release()
      }
    }
  }

  private fun startAmbientMusic() {
    if (musicJob?.isActive == true) return
    musicJob =
      scope.launch {
        val notes = listOf(220.0, 261.63, 293.66, 329.63, 261.63, 196.0)
        var idx = 0
        while (isActive && musicEnabled && enabled) {
          val freq = notes[idx % notes.size]
          idx++
          audioSemaphore.withPermit {
            playAmbientNote(freq, durationMs = 1400, gain = (soundVolume * 0.28f).coerceIn(0f, 0.4f))
          }
          delay(250L)
        }
      }
  }

  private fun stopAmbientMusic() {
    musicJob?.cancel()
    musicJob = null
  }

  private suspend fun playAmbientNote(freq: Double, durationMs: Int, gain: Float) {
    if (gain <= 0.01f) return
    val sampleRate = 22050
    val count = (sampleRate * durationMs) / 1000
    val pcm = ShortArray(count)
    var phase = 0.0
    for (i in 0 until count) {
      val t = i.toDouble() / count
      val env = sin(t * PI)
      phase += 2.0 * PI * freq / sampleRate
      val wave = sin(phase) * 0.7 + sin(phase * 0.5) * 0.3
      pcm[i] = (wave * env * 4500.0 * gain).toInt().coerceIn(-32767, 32767).toShort()
    }
    writeAndPlay(pcm, sampleRate, durationMs)
  }

  private suspend fun playToneBuffer(durationMs: Int, mode: Int, gain: Float) {
    val sampleRate = 22050
    val sampleCount = (sampleRate * durationMs) / 1000
    val pcm = ShortArray(sampleCount)
    var phase = 0.0

    for (i in 0 until sampleCount) {
      val t = i.toDouble() / sampleCount
      when (mode) {
        0 -> {
          // Air-raid siren sweep
          val sweep = 0.5 - 0.5 * kotlin.math.cos(t * PI * 2.0)
          val freq = 390.0 + sweep * 370.0
          phase += 2.0 * PI * freq / sampleRate
          val env =
            when {
              t < 0.08 -> t / 0.08
              t > 0.88 -> (1.0 - t) / 0.12
              else -> 1.0
            }
          val wave = sin(phase) * 0.7 + sin(phase * 1.5) * 0.25
          pcm[i] = (wave * env * 9000.0 * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
        1 -> {
          // Explosion thud
          val freq = 135.0 * (1.0 - t * 0.72)
          phase += 2.0 * PI * freq / sampleRate
          val env = (1.0 - t) * (1.0 - t)
          val noise = ((i * 1103515245 + 12345) and 0x7FFF) / 16384.0 - 1.0
          val wave = sin(phase) * 0.6 + noise * 0.4
          pcm[i] = (wave * env * 11000.0 * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
        else -> {
          // PVO Interception chirp
          val freq = 620.0 + t * 520.0
          phase += 2.0 * PI * freq / sampleRate
          val env = 1.0 - t
          pcm[i] = (sin(phase) * env * 7000.0 * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
      }
    }
    writeAndPlay(pcm, sampleRate, durationMs)
  }

  private suspend fun writeAndPlay(pcm: ShortArray, sampleRate: Int, durationMs: Int) {
    var track: AudioTrack? = null
    try {
      val minBuf =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      val bufSize = maxOf(minBuf, pcm.size * 2)
      track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufSize)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

      track.write(pcm, 0, pcm.size)
      track.play()
      delay(durationMs.toLong() + 30L)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      // Ignore audio errors on headless/muted environments
    } finally {
      try {
        track?.pause()
        track?.flush()
        track?.stop()
      } catch (_: Throwable) {}
      try {
        track?.release()
      } catch (_: Throwable) {}
    }
  }

  fun release() {
    sirenJob?.cancel()
    musicJob?.cancel()
  }
}
