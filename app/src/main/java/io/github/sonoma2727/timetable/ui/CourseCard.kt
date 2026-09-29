package io.github.sonoma2727.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sonoma2727.timetable.data.model.Course
import kotlin.math.abs

private val coursePaletteSize = 20

private fun slotRgb(index: Int): DoubleArray {
    val hue = (index * 137.508) % 360.0
    val sat = 0.50 + 0.10 * ((index * 0.6180339887 + 0.66) % 1.0)
    val lig = 0.37 + 0.26 * ((index * 0.4142135623) % 1.0)
    val c = (1 - kotlin.math.abs(2 * lig - 1)) * sat
    val hp = hue / 60.0
    val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
    var r = 0.0
    var g = 0.0
    var b = 0.0
    when {
        hp < 1 -> { r = c; g = x }
        hp < 2 -> { r = x; g = c }
        hp < 3 -> { g = c; b = x }
        hp < 4 -> { g = x; b = c }
        hp < 5 -> { r = x; b = c }
        else -> { r = c; b = x }
    }
    val m = lig - c / 2
    return doubleArrayOf((r + m) * 255, (g + m) * 255, (b + m) * 255)
}

private val slotColors: List<DoubleArray> = List(coursePaletteSize) { slotRgb(it) }
private val coursePalette: List<Color> = slotColors.map {
    Color(red = (it[0] / 255.0).toFloat(), green = (it[1] / 255.0).toFloat(), blue = (it[2] / 255.0).toFloat())
}

private fun rgbDist2(a: DoubleArray, b: DoubleArray): Double {
    val dr = a[0] - b[0]
    val dg = a[1] - b[1]
    val db = a[2] - b[2]
    return dr * dr + dg * dg + db * db
}

private val groupSuffixRegex = Regex("""\s*[（(]分组[^）)]*[）)]""")
private val colorIndexByName = mutableMapOf<String, Int>()

fun installCourseColors(courses: Collection<Course>) {
    val keys = courses.map { it.courseName.replace(groupSuffixRegex, "").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
    colorIndexByName.clear()
    val used = mutableSetOf<Int>()
    for (key in keys) {
        var best = 0
        var bestScore = -1.0
        for (i in 0 until coursePaletteSize) {
            if (i in used) continue
            var minD = Double.MAX_VALUE
            for (u in used) minD = minOf(minD, rgbDist2(slotColors[i], slotColors[u]))
            if (minD > bestScore) {
                bestScore = minD
                best = i
            }
        }
        colorIndexByName[key] = best
        used.add(best)
        if (used.size == coursePaletteSize) used.clear()
    }
}

fun courseColor(course: Course): Color {
    val key = course.courseName.replace(groupSuffixRegex, "").trim()
    val index = colorIndexByName[key] ?: (abs(course.courseName.hashCode()) % coursePaletteSize)
    return coursePalette[index]
}

@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit = {},
) {
    val accent = courseColor(course)
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = if (dimmed) 0.16f else 0.30f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = if (dimmed) 0.25f else 0.65f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = course.timeStart,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    text = course.timeEnd,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(course.room, course.teacher)
                        .joinToString(" · ")
                        .ifEmpty { course.periodLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CourseDetailDialog(course: Course, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.courseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("教师", course.teacher)
                DetailRow("教室", course.room)
                DetailRow(
                    "时间",
                    listOfNotNull(
                        course.dayName.takeIf { it.isNotBlank() },
                        listOfNotNull(course.timeStart, course.timeEnd)
                            .joinToString(" - ")
                            .takeIf { it.isNotBlank() },
                    ).joinToString(" "),
                )
                DetailRow("周次", course.weekText)
                DetailRow("节次", course.periods.joinToString(",") { "%02d".format(it) }.takeIf { it.isNotBlank() })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CourseBlock(
    course: Course,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val accent = courseColor(course)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Text(
            text = course.courseName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = course.room.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
