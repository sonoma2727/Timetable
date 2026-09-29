package io.github.sonoma2727.timetable.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Weeks {

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(DayOfWeek.MONDAY)

    fun currentWeek(firstWeekMondayEpochDay: Long?, today: LocalDate = LocalDate.now()): Int? {
        if (firstWeekMondayEpochDay == null) return null
        val firstMonday = LocalDate.ofEpochDay(firstWeekMondayEpochDay)
        val diff = ChronoUnit.DAYS.between(firstMonday, today)
        return (diff / 7 + 1).toInt().coerceAtLeast(1)
    }

    fun todayIndex(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value
}
