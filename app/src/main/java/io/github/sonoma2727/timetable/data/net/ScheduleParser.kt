package io.github.sonoma2727.timetable.data.net

import io.github.sonoma2727.timetable.data.model.Course
import io.github.sonoma2727.timetable.data.model.TimeSlot
import io.github.sonoma2727.timetable.data.model.WeekInfo

object ScheduleParser {

    class ParseException(message: String) : Exception(message)

    data class ParsedTable(
        val days: List<String>,
        val times: List<TimeSlot>,
        val courses: List<Course>,
    )

    private val dotAll = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val fontRegex = Regex("""<font[^>]*title=['"]([^'"]*)['"][^>]*>(.*?)</font>""", dotAll)
    private val tagRegex = Regex("<[^>]+>")
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val sepRegex = Regex("-{5,}")
    private val weekValRegex = Regex("""([\d,\s、，\-]+)\(([周单双])\)""")
    private val timeRegex = Regex("""(\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2})""")

    fun stripTags(value: String): String {
        var s = brRegex.replace(value, "\n")
        s = tagRegex.replace(s, "")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
        return Regex("""[ \t]+""").replace(s, " ").trim()
    }

    fun extractWeekInfo(html: String): WeekInfo {
        var term: String? = null
        var template: String? = null
        var weeks: List<Int> = emptyList()
        for (name in listOf("xnxq01id", "zc", "kbjcmsid")) {
            val select = Regex(
                """<select[^>]*name=["']$name["'][^>]*>(.*?)</select>""",
                dotAll,
            ).find(html) ?: continue
            val opts = Regex(
                """<option[^>]*value=["']([^"']*)["']([^>]*)>(.*?)</option>""",
                dotAll,
            ).findAll(select.groupValues[1])
            if (name == "zc") {
                weeks = opts.mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            } else {
                opts.forEach { opt ->
                    if ("selected" in opt.groupValues[2]) {
                        val text = stripTags(opt.groupValues[3])
                        if (name == "xnxq01id") term = text else template = text
                    }
                }
            }
        }
        if (weeks.isEmpty()) {
            weeks = Regex("""[?&]zc=(\d+)""").findAll(html)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .distinct().sorted().toList()
        }
        return WeekInfo(term = term, timeTemplate = template, weeks = weeks)
    }

    fun parseTable(html: String): ParsedTable {
        val table = Regex(
            """<table[^>]*id=["']kbtable["'][^>]*>(.*?)</table>""",
            dotAll,
        ).find(html)?.groupValues?.get(1)
            ?: throw ParseException("kbtable not found")

        val trs = Regex("""<tr[^>]*>(.*?)</tr>""", dotAll).findAll(table)
            .map { it.groupValues[1] }.toList()
        if (trs.isEmpty()) throw ParseException("empty kbtable")

        val days = Regex("""<th[^>]*>(.*?)</th>""", dotAll).findAll(trs[0])
            .map { stripTags(it.groupValues[1]) }.toList().drop(1)

        val rowsRaw = mutableListOf<List<Pair<String, String>>>()
        val times = mutableListOf<TimeSlot>()
        for (i in 1 until trs.size) {
            val cells = Regex("""(<t[dh][^>]*>)(.*?)</t[dh]>""", dotAll).findAll(trs[i])
                .map { it.groupValues[1] to it.groupValues[2] }.toList()
            if (cells.isEmpty()) continue
            val headerText = stripTags(cells.first().second)
            val timeMatch = timeRegex.find(headerText)
            val timeRange = timeMatch?.groupValues?.get(1)?.replace(" ", "") ?: ""
            val label = if (timeMatch != null) {
                headerText.replace(timeMatch.groupValues[1], "").trim()
            } else {
                headerText
            }
            times.add(TimeSlot(label = label, time = timeRange))
            rowsRaw.add(cells.drop(1))
        }

        val rowFirst = mutableMapOf<Int, Int>()
        val periodToRow = mutableMapOf<Int, Int>()
        var nextPeriod = 1
        times.forEachIndexed { r, slot ->
            val dash = slot.time.indexOf('-')
            if (dash <= 0) return@forEachIndexed
            val a = slot.time.substring(0, dash)
            val b = slot.time.substring(dash + 1)
            if (a.length < 5 || b.length < 5) return@forEachIndexed
            val startMin = a.substring(0, 2).toInt() * 60 + a.substring(3, 5).toInt()
            val endMin = b.substring(0, 2).toInt() * 60 + b.substring(3, 5).toInt()
            val dur = endMin - startMin
            val n = ((dur + 5) / 50).coerceAtLeast(1)
            rowFirst[r] = nextPeriod
            for (i in 0 until n) periodToRow[nextPeriod + i] = r
            nextPeriod += n
        }

        fun periodBounds(period: Int): Pair<Int, Int>? {
            val r = periodToRow[period] ?: return null
            val time = times[r].time
            val dash = time.indexOf('-')
            if (dash <= 0) return null
            val a = time.substring(0, dash)
            if (a.length < 5) return null
            val base = a.substring(0, 2).toInt() * 60 + a.substring(3, 5).toInt() +
                (period - (rowFirst[r] ?: 1)) * 50
            return base to base + 45
        }

        fun hhmm(minutes: Int): String =
            "%02d:%02d".format(minutes / 60, minutes % 60)

        val schedule = mutableListOf<Course>()
        val seen = mutableSetOf<List<Any>>()
        rowsRaw.forEachIndexed { r, cells ->
            val slotLabel = times[r].label
            if (slotLabel.contains("备注")) return@forEachIndexed
            cells.forEachIndexed { c, (_, content) ->
                if (c >= days.size || content.isBlank()) return@forEachIndexed
                for (raw in parseCell(content)) {
                    var periods = raw.periods
                    if (periods.isEmpty()) {
                        val first = rowFirst[r] ?: 1
                        periods = (first until first + 1).filter { periodToRow[it] == r }
                        if (periods.isEmpty()) periods = listOf(1)
                    }
                    val rows = periods.mapNotNull { periodToRow[it] }.sorted()
                    val startRow = rows.firstOrNull() ?: r
                    val endRow = rows.lastOrNull() ?: r
                    val starts = periods.mapNotNull { periodBounds(it)?.first }
                    val ends = periods.mapNotNull { periodBounds(it)?.second }
                    val course = raw.copy(
                        day = c + 1,
                        dayName = days.getOrNull(c) ?: (c + 1).toString(),
                        row = startRow,
                        spanRows = endRow - startRow + 1,
                        periodLabel = times[startRow].label,
                        periods = periods,
                        timeStart = if (starts.isNotEmpty()) hhmm(starts.min()) else "",
                        timeEnd = if (ends.isNotEmpty()) hhmm(ends.max()) else "",
                    )
                    val key = listOf<Any>(
                        course.day, course.courseName, course.teacher ?: "",
                        course.room ?: "", course.weekText ?: "", course.periods,
                    )
                    if (!seen.add(key)) continue
                    schedule.add(course)
                }
            }
        }
        schedule.sortWith(compareBy({ it.day }, { it.row }, { it.periods.firstOrNull() ?: 0 }))

        val visibleTimes = times.filter { it.time.isNotBlank() }
        val validCourses = schedule.filter { it.row < visibleTimes.size }
        return ParsedTable(days, visibleTimes, validCourses)
    }

    private fun parseCell(tdHtml: String): List<Course> {
        var divs = Regex("""<div[^>]*class=["']kbcontent["'][^>]*>(.*?)</div>""", dotAll)
            .findAll(tdHtml).map { it.groupValues[1] }.toList()
        if (divs.isEmpty()) {
            divs = Regex("""<div[^>]*class=["']kbcontent1["'][^>]*>(.*?)</div>""", dotAll)
                .findAll(tdHtml).map { it.groupValues[1] }.toList()
        }
        val courses = mutableListOf<Course>()
        for (div in divs) {
            if ("&nbsp;" in div && !fontRegex.containsMatchIn(div)) continue
            for (part in sepRegex.split(div)) {
                val course = parseBlock(part) ?: continue
                if (course.courseName.isNotBlank() || course.weekText.isNotBlank()) {
                    courses.add(course)
                }
            }
        }
        return courses
    }

    private fun parseBlock(blockHtml: String): Course? {
        val fonts = fontRegex.findAll(blockHtml)
            .associate { it.groupValues[1] to stripTags(it.groupValues[2]) }
        var name = stripTags(fontRegex.split(blockHtml).first())
        name = name.split(Regex("""\s+""")).joinToString(" ").trim().trim('-', ' ').trim()
        if (name.isEmpty() && fonts.isEmpty()) return null

        val course = Course(courseName = name)
        val teacher = fonts["老师"] ?: fonts["教师"]
        val room = fonts["教室"]
        val weekVal = fonts.entries.firstOrNull { it.key.startsWith("周次") }?.value
        val weeks = mutableListOf<Int>()
        var periods = emptyList<Int>()
        var weekText: String? = null
        if (weekVal != null) {
            weekText = weekVal
            val parsed = parseWeeks(weekVal)
            weeks.addAll(parsed.first)
            periods = parsed.second
        }
        return course.copy(
            teacher = teacher,
            room = room,
            weekText = weekText ?: "",
            weeks = weeks,
            periods = periods,
        )
    }

    private fun parseWeeks(value: String): Pair<List<Int>, List<Int>> {
        val weeks = sortedSetOf<Int>()
        for (m in weekValRegex.findAll(value)) {
            val kind = m.groupValues[2]
            val body = m.groupValues[1].replace("，", ",").replace("、", ",").replace(" ", "")
            for (part in body.split(",")) {
                if (part.isEmpty()) continue
                val range = try {
                    val dash = part.indexOf('-')
                    if (dash > 0) {
                        part.substring(0, dash).toInt() to part.substring(dash + 1).toInt()
                    } else {
                        part.toInt() to part.toInt()
                    }
                } catch (e: NumberFormatException) {
                    continue
                }
                for (w in range.first..range.second) {
                    if (kind == "单" && w % 2 == 0) continue
                    if (kind == "双" && w % 2 == 1) continue
                    weeks.add(w)
                }
            }
        }
        val bracket = value.indexOf('[')
        val periods = if (bracket >= 0) {
            Regex("""\d{1,2}""").findAll(value.substring(bracket))
                .mapNotNull { it.value.toIntOrNull() }
                .toSortedSet().toList()
        } else {
            emptyList()
        }
        return weeks.toList() to periods
    }
}
