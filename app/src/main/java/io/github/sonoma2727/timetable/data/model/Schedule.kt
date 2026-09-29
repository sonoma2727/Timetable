package io.github.sonoma2727.timetable.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeSlot(
    val label: String = "",
    val time: String = "",
)

@Serializable
data class Course(
    val courseName: String,
    val teacher: String? = null,
    val room: String? = null,
    val day: Int = 1,
    val row: Int = 0,
    val spanRows: Int = 1,
    val periods: List<Int> = emptyList(),
    val timeStart: String = "",
    val timeEnd: String = "",
    val periodLabel: String = "",
    val dayName: String = "",
    val weekText: String = "",
    val weeks: List<Int> = emptyList(),
) {
    fun inWeek(week: Int): Boolean = weeks.isEmpty() || week in weeks
}

@Serializable
data class WeekInfo(
    val term: String? = null,
    val timeTemplate: String? = null,
    val weeks: List<Int> = emptyList(),
)

@Serializable
data class Schedule(
    val source: String = "",
    val fetchedAt: Long = 0L,
    val weekInfo: WeekInfo = WeekInfo(),
    val days: List<String> = emptyList(),
    val times: List<TimeSlot> = emptyList(),
    val courses: List<Course> = emptyList(),
) {
    fun coursesOfWeek(week: Int): List<Course> = courses.filter { it.inWeek(week) }
}
