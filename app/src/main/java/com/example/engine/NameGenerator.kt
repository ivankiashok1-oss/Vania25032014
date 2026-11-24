package com.example.engine

import com.example.model.FlagPattern
import com.example.model.NationFlag
import kotlin.random.Random

object NameGenerator {
  private val nationPrefixes =
    listOf(
      "Республика",
      "Федерация",
      "Союз",
      "Конфедерация",
      "Королевство",
      "Альянс",
      "Держава",
      "Директория",
    )

  private val nationRoots =
    listOf(
      "Валория",
      "Нордхейвен",
      "Корсак",
      "Солярис",
      "Аурелия",
      "Весперия",
      "Аркадия",
      "Тарга",
      "Остмарк",
      "Кальдария",
      "Северия",
      "Мираж",
      "Элизиум",
      "Гранития",
      "Белара",
      "Астралия",
    )

  private val cityNames =
    listOf(
      "Новый Свет",
      "Железноград",
      "Порт-Вест",
      "Красногорье",
      "Солнечный",
      "Броневой",
      "Восток-4",
      "Тихая Гавань",
      "Авангард",
      "Стальноводск",
      "Зеленодольск",
      "Маяк",
      "Южнопорт",
      "Светлогорск",
      "Рубеж",
      "Цитадель",
      "Грозовой",
      "Береговой",
      "Лазурный",
      "Кремний",
      "Высотный",
      "Озёрный",
      "Перевал",
      "Арсенальск",
    )

  private val firstNames =
    listOf(
      "Алексей",
      "Мария",
      "Дмитрий",
      "Анна",
      "Иван",
      "Елена",
      "Николай",
      "Ольга",
      "Сергей",
      "Виктория",
      "Артём",
      "Дарья",
      "Максим",
      "София",
      "Роман",
      "Ксения",
      "Павел",
      "Алиса",
      "Андрей",
      "Кира",
    )

  private val lastNames =
    listOf(
      "Громов",
      "Соколова",
      "Морозов",
      "Волкова",
      "Орлов",
      "Лебедева",
      "Ковалёв",
      "Белова",
      "Зайцев",
      "Крылова",
      "Тихонов",
      "Полякова",
      "Руднев",
      "Ветрова",
      "Корнеев",
      "Семенова",
    )

  val nationColors =
    listOf(
      0xFF38BDF8.toInt(), // Sky Cyan
      0xFFEF4444.toInt(), // Crimson
      0xFF22C55E.toInt(), // Emerald
      0xFFF59E0B.toInt(), // Amber
      0xFFA855F7.toInt(), // Violet
      0xFFEC4899.toInt(), // Magenta
      0xFF14B8A6.toInt(), // Teal
      0xFFF97316.toInt(), // Orange
      0xFF6366F1.toInt(), // Indigo
      0xFF84CC16.toInt(), // Lime
    )

  fun randomNationName(rng: Random, usedNames: Set<String>): String {
    repeat(24) {
      val prefix = nationPrefixes[rng.nextInt(nationPrefixes.size)]
      val root = nationRoots[rng.nextInt(nationRoots.size)]
      val candidate = "$prefix $root"
      if (candidate !in usedNames) return candidate
    }
    return "Союз ${nationRoots[rng.nextInt(nationRoots.size)]}-${rng.nextInt(10, 99)}"
  }

  fun randomCityName(rng: Random, usedNames: Set<String>): String {
    repeat(24) {
      val candidate = cityNames[rng.nextInt(cityNames.size)]
      if (candidate !in usedNames) return candidate
    }
    return "${cityNames[rng.nextInt(cityNames.size)]}-${rng.nextInt(2, 19)}"
  }

  fun randomCitizenName(rng: Random): String {
    val first = firstNames[rng.nextInt(firstNames.size)]
    val last = lastNames[rng.nextInt(lastNames.size)]
    return "$first $last"
  }

  fun randomFlag(primaryColor: Int, rng: Random): NationFlag {
    val patterns = FlagPattern.entries
    val secondaryPool = listOf(0xFFF8FAFC.toInt(), 0xFF0F172A.toInt(), 0xFFFACC15.toInt(), 0xFFCBD5E1.toInt())
    val accentPool = listOf(0xFFFACC15.toInt(), 0xFFFFFFFF.toInt(), 0xFF38BDF8.toInt(), 0xFFEF4444.toInt())
    return NationFlag(
      pattern = patterns[rng.nextInt(patterns.size)],
      primaryColor = primaryColor,
      secondaryColor = secondaryPool[rng.nextInt(secondaryPool.size)],
      accentColor = accentPool[rng.nextInt(accentPool.size)],
    )
  }
}
