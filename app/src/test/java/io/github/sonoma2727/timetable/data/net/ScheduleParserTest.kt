package io.github.sonoma2727.timetable.data.net

import io.github.sonoma2727.timetable.data.model.Course
import io.github.sonoma2727.timetable.data.model.Schedule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class
ScheduleParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name))
            .bufferedReader(Charsets.UTF_8)
            .readText()

    @Test
    fun kotlinParserMatchesPythonBaseline() {
        val html = load("xskb_wcurrent.html")
        val expected = json.decodeFromString<Schedule>(load("parsed.json"))

        val table = ScheduleParser.parseTable(html)
        val actual = Schedule(
            weekInfo = ScheduleParser.extractWeekInfo(html),
            days = table.days,
            times = table.times,
            courses = table.courses,
        )

        assertEquals(expected.days, actual.days)
        assertEquals(
            expected.times.filter { it.time.isNotBlank() },
            actual.times,
        )
        assertEquals(expected.weekInfo.term, actual.weekInfo.term)
        assertEquals(expected.weekInfo.timeTemplate, actual.weekInfo.timeTemplate)
        assertEquals(expected.weekInfo.weeks, actual.weekInfo.weeks)
        assertEquals(24, actual.courses.size)
        assertEquals(
            expected.courses.map(::normalize).sorted(),
            actual.courses.map(::normalize).sorted(),
        )
    }

    private fun normalize(c: Course): String = listOf(
        c.courseName,
        c.teacher.orEmpty(),
        c.room.orEmpty(),
        c.day.toString(),
        c.row.toString(),
        c.spanRows.toString(),
        c.periods.joinToString(","),
        c.timeStart,
        c.timeEnd,
        c.periodLabel,
        c.dayName,
        c.weekText,
        c.weeks.joinToString(","),
    ).joinToString("|")
}
