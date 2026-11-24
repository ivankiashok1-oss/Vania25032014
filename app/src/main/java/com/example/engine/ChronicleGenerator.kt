package com.example.engine

import com.example.BuildConfig
import com.example.model.City
import com.example.model.Nation
import com.example.model.WarConflict
import com.example.model.WeatherType
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ChronicleSnapshot(
  val year: Int,
  val month: Int,
  val weather: WeatherType,
  val population: Int,
  val nations: List<Nation>,
  val cities: List<City>,
  val warConflicts: List<WarConflict>,
  val totalPvoIntercepts: Int,
)

object ChronicleGenerator {
  private const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

  suspend fun generateChronicle(
    snapshot: ChronicleSnapshot,
    apiKeyOverride: String? = null,
  ): String =
    withContext(Dispatchers.IO) {
      val apiKey = (apiKeyOverride ?: BuildConfig.GEMINI_API_KEY).trim()
      if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
        return@withContext generateOfflineChronicle(snapshot)
      }

      try {
        val contextPrompt = buildContextPrompt(snapshot)
        val bodyJson =
          JSONObject().apply {
            put(
              "contents",
              JSONArray().put(
                JSONObject().apply {
                  put(
                    "parts",
                    JSONArray().put(
                      JSONObject().put("text", contextPrompt)
                    ),
                  )
                }
              ),
            )
            put(
              "generationConfig",
              JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 280)
              },
            )
          }

        val conn = (URL(GEMINI_ENDPOINT).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 6000
          readTimeout = 6000
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=UTF-8")
          setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
          OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(bodyJson.toString())
            writer.flush()
          }

          if (conn.responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = extractTextFromResponse(responseText)
            if (!parsed.isNullOrBlank()) {
              return@withContext parsed.trim()
            }
          }
        } finally {
          conn.disconnect()
        }
      } catch (_: Throwable) {
        // Fallback to deterministic local analytical chronicle on any network/API error
      }

      generateOfflineChronicle(snapshot)
    }

  internal fun buildContextPrompt(snapshot: ChronicleSnapshot): String {
    val cityCounts = snapshot.cities.groupingBy { it.nationId }.eachCount()
    val topNations =
      snapshot.nations
        .sortedByDescending { cityCounts[it.id] ?: 0 }
        .take(5)
        .joinToString("; ") { n ->
          val cities = cityCounts[n.id] ?: 0
          "${n.name} (городов: $cities, побед: ${n.wins}, потерь: ${n.totalCasualties}, захвачено тайлов: ${n.territoriesCaptured}, ПВО: ${n.airDefenseIntercepts})"
        }
        .ifEmpty { "государств пока нет" }

    val nationById = snapshot.nations.associateBy { it.id }
    val activeWars = snapshot.warConflicts.count { it.isActive }
    val worstConflict =
      snapshot.warConflicts.maxByOrNull { it.casualtiesA + it.casualtiesB }?.let { wc ->
        val nameA = nationById[wc.nationAId]?.name ?: "Гос.#${wc.nationAId}"
        val nameB = nationById[wc.nationBId]?.name ?: "Гос.#${wc.nationBId}"
        val totalCas = wc.casualtiesA + wc.casualtiesB
        "$nameA против $nameB (потерь: $totalCas, захвачено тайлов: ${wc.tilesCapturedA + wc.tilesCapturedB}, статус: ${if (wc.isActive) "идёт" else "перемирие"})"
      } ?: "крупных конфликтов не зафиксировано"

    return buildString {
      appendLine("Ты — тактический военный хронист пиксельной песочницы «Pixel World».")
      appendLine("Напиши короткую атмосферную газетную сводку эпохи на русском языке (строго 3–4 предложения, без markdown-заголовков).")
      appendLine("Фактические данные мира:")
      appendLine("- Дата: ${snapshot.year} год, месяц ${snapshot.month}, погода: ${snapshot.weather.titleRu}")
      appendLine("- Население: ${snapshot.population}, государств: ${snapshot.nations.size}, городов: ${snapshot.cities.size}")
      appendLine("- Ведущие державы: $topNations")
      appendLine("- Активных войн: $activeWars (всего войн: ${snapshot.warConflicts.size}), главный конфликт: $worstConflict")
      appendLine("- Перехватов ПВО по миру: ${snapshot.totalPvoIntercepts}")
    }
  }

  fun generateOfflineChronicle(snapshot: ChronicleSnapshot): String {
    val dateLabel = "${snapshot.year}.${snapshot.month.toString().padStart(2, '0')}"
    val weatherPhrase = snapshot.weather.titleRu.lowercase()

    if (snapshot.nations.isEmpty()) {
      return "Хроника мира ($dateLabel, погода: $weatherPhrase): на планете с населением ${snapshot.population} чел. пока не сформированы суверенные державы. Свободные земли ожидают основания первых городов и появления цивилизаций."
    }

    val cityCounts = snapshot.cities.groupingBy { it.nationId }.eachCount()
    val leader =
      snapshot.nations.maxByOrNull { n ->
        (cityCounts[n.id] ?: 0) * 100 + n.territoriesCaptured + n.wins * 5
      } ?: snapshot.nations.first()
    val leaderCities = cityCounts[leader.id] ?: 0

    val nationById = snapshot.nations.associateBy { it.id }
    val activeWars = snapshot.warConflicts.count { it.isActive }
    val worstConflict = snapshot.warConflicts.maxByOrNull { it.casualtiesA + it.casualtiesB }

    val conflictSentence =
      if (worstConflict != null) {
        val nameA = nationById[worstConflict.nationAId]?.name ?: "Держава #${worstConflict.nationAId}"
        val nameB = nationById[worstConflict.nationBId]?.name ?: "Держава #${worstConflict.nationBId}"
        val totalCas = worstConflict.casualtiesA + worstConflict.casualtiesB
        val statusText = if (worstConflict.isActive) "продолжается ожесточённое противостояние" else "удалось заключить перемирие"
        "В центре внимания остаётся конфликт между $nameA и $nameB, где $statusText (общие потери: $totalCas, перераспределено тайлов: ${worstConflict.tilesCapturedA + worstConflict.tilesCapturedB})."
      } else {
        "Крупных межгосударственных войн в текущую эпоху не зафиксировано, а державы сосредоточены на росте городов и укреплении границ."
      }

    val pvoSentence =
      if (snapshot.totalPvoIntercepts > 0) {
        "Расчёты ПВО уже перехватили ${snapshot.totalPvoIntercepts} возд. целей, снизив угрозу инфраструктуре."
      } else {
        "Системы ПВО находятся в режиме дежурства на случай воздушных налётов."
      }

    val forecast =
      when {
        activeWars >= 2 -> "Прогноз: высокая напряжённость сохраняется — исход кампании решат снабжение армий и контроль ключевых столиц."
        activeWars == 1 -> "Прогноз: баланс сил остаётся хрупким, и затянувшийся конфликт может перекроить карту региона."
        else -> "Прогноз: при текущем росте населения (${snapshot.population} чел.) державы готовятся к новой волне экспансии."
      }

    return "Сводка эпохи ($dateLabel, $weatherPhrase): безусловным лидером карты выступает ${leader.name} ($leaderCities гор., захвачено терр.: ${leader.territoriesCaptured}). $conflictSentence $pvoSentence $forecast"
  }

  private fun extractTextFromResponse(responseJson: String): String? {
    val root = JSONObject(responseJson)
    val candidates = root.optJSONArray("candidates") ?: return null
    if (candidates.length() == 0) return null
    val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
    val parts = content.optJSONArray("parts") ?: return null
    if (parts.length() == 0) return null
    return parts.getJSONObject(0).optString("text", "").takeIf { it.isNotBlank() }
  }
}
