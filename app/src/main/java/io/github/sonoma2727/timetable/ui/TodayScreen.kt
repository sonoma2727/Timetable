package io.github.sonoma2727.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sonoma2727.timetable.data.Weeks
import io.github.sonoma2727.timetable.data.model.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun TodayScreen(
    state: UiState,
    onGoSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val today = LocalDate.now()
    val schedule = state.schedule
    val week = state.firstWeekMonday?.let { Weeks.currentWeek(it, today) }
    val courses = remember(schedule, week, today) {
        val base = when {
            schedule == null -> emptyList()
            week == null -> schedule.courses
            else -> schedule.coursesOfWeek(week)
        }
        base.filter { it.day == today.dayOfWeek.value }.sortedBy { it.timeStart }
    }

    val now = LocalTime.now()
    var detail by remember { mutableStateOf<Course?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("M月d日")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(dayNames[today.dayOfWeek.value])
                        if (week != null) append(" · 第").append(week).append("周")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (schedule != null) {
                Text(
                    text = when (week) {
                        null -> "未按周次过滤"
                        else -> schedule.weekInfo.term.orEmpty()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.firstWeekMonday == null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "尚未设置开学日期，无法计算当前周次",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onGoSettings) { Text("去设置") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            schedule == null -> EmptyState(
                text = "暂无课表数据",
                actionLabel = "重新加载",
                onAction = onRetry,
            )

            courses.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (week == null) "今天没有课" else "今天没有课，好好休息",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val statuses = courses.map { courseStatus(it, now) }
                val nextIndex = statuses.indexOf(Status.UPCOMING)
                courses.forEachIndexed { index, course ->
                    val status = statuses[index]
                    Spacer(Modifier.height(8.dp))
                    CourseCard(
                        course = course,
                        dimmed = status == Status.PAST,
                        badge = when {
                            status == Status.ONGOING -> "进行中"
                            index == nextIndex -> "下一节"
                            else -> null
                        },
                        onClick = { detail = course },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    detail?.let { CourseDetailDialog(course = it, onDismiss = { detail = null }) }
}

private enum class Status { PAST, ONGOING, UPCOMING }

private fun courseStatus(course: Course, now: LocalTime): Status {
    val start = parseTime(course.timeStart)
    val end = parseTime(course.timeEnd)
    return when {
        start == null -> Status.UPCOMING
        now < start -> Status.UPCOMING
        end != null && now >= end -> Status.PAST
        else -> Status.ONGOING
    }
}

private fun parseTime(value: String): LocalTime? = try {
    if (value.isBlank()) null else LocalTime.parse(value)
} catch (e: Exception) {
    null
}

@Composable
private fun EmptyState(text: String, actionLabel: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
