package io.github.sonoma2727.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import io.github.sonoma2727.timetable.data.model.Course
import io.github.sonoma2727.timetable.data.model.Schedule
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TIME_COL = 46.dp
private val ROW_MIN_HEIGHT = 84.dp

@Composable
fun WeekScreen(
    schedule: Schedule?,
    currentWeek: Int?,
    onGoSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    var detail by remember { mutableStateOf<Course?>(null) }

    if (schedule == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无课表数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("重新加载") }
            }
        }
        return
    }

    val weeks = remember(schedule) {
        schedule.weekInfo.weeks.ifEmpty { (1..18).toList() }
    }
    val today = LocalDate.now()
    val pagerState = rememberPagerState(
        initialPage = weeks.indexOf(currentWeek ?: weeks.first()).coerceAtLeast(0),
        pageCount = { weeks.size },
    )
    val scope = rememberCoroutineScope()
    val selectedWeek = weeks[pagerState.currentPage]

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                enabled = pagerState.currentPage > 0,
            ) { Text("◀") }

            WeekPicker(
                weeks = weeks,
                selected = selectedWeek,
                onSelect = { week ->
                    val index = weeks.indexOf(week)
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            TextButton(
                onClick = {
                    if (pagerState.currentPage < weeks.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                enabled = pagerState.currentPage < weeks.lastIndex,
            ) { Text("▶") }

            Spacer(Modifier.weight(1f))

            if (currentWeek != null && currentWeek in weeks && selectedWeek != currentWeek) {
                TextButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(weeks.indexOf(currentWeek)) }
                    },
                ) { Text("本周") }
            }
        }

        if (currentWeek == null) {
            TextButton(onClick = onGoSettings, modifier = Modifier.padding(start = 8.dp)) {
                Text("未设置开学日期，点击设置以定位本周")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            WeekGrid(
                schedule = schedule,
                week = weeks[page],
                currentWeek = currentWeek,
                today = today,
                onCourseClick = { detail = it },
            )
        }
    }

    detail?.let { CourseDetailDialog(course = it, onDismiss = { detail = null }) }
}

@Composable
private fun WeekPicker(
    weeks: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "第${selected}周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            weeks.forEach { week ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "第${week}周",
                            color = if (week == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(week)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    schedule: Schedule,
    week: Int,
    currentWeek: Int?,
    today: LocalDate,
    onCourseClick: (Course) -> Unit,
) {
    val times = schedule.times
    val days = schedule.days
    if (times.isEmpty() || days.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无课节时间数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val courses = schedule.coursesOfWeek(week).filter {
        it.day in 1..days.size && it.row in times.indices
    }
    val isCurrentWeek = currentWeek != null && week == currentWeek
    val todayIndex = today.dayOfWeek.value
    val autoFitStyle = LocalTextStyle.current.copy(
        fontSize = TextUnit.Unspecified,
        lineHeight = TextUnit.Unspecified,
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Spacer(Modifier.width(TIME_COL))
            days.forEachIndexed { i, name ->
                val highlight = isCurrentWeek && (i + 1) == todayIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name.replaceFirst("星期", "周"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                        color = if (highlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (highlight) {
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(50),
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            val cellW = (maxWidth - TIME_COL) / days.size
            val rowH = maxOf(maxHeight / times.size, ROW_MIN_HEIGHT)
            val gridColor = MaterialTheme.colorScheme.outlineVariant

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowH * times.size)
                    .verticalScroll(rememberScrollState()),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val cw = cellW.toPx()
                    val rh = rowH.toPx()
                    val tc = TIME_COL.toPx()
                    val stroke = 1.dp.toPx()
                    val rowBottom = times.size * rh
                    for (r in 0..times.size) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, r * rh),
                            end = Offset(size.width, r * rh),
                            strokeWidth = stroke,
                        )
                    }
                    for (c in 0..days.size) {
                        drawLine(
                            color = gridColor,
                            start = Offset(tc + c * cw, 0f),
                            end = Offset(tc + c * cw, rowBottom),
                            strokeWidth = stroke,
                        )
                    }
                }

                times.forEachIndexed { index, slot ->
                    Column(
                        modifier = Modifier
                            .offset(x = 0.dp, y = rowH * index)
                            .width(TIME_COL)
                            .height(rowH)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = slot.time.replace("-", "\n"),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }

                courses.groupBy { it.row to it.day }.forEach { (_, cellCourses) ->
                    cellCourses.forEachIndexed { idx, course ->
                        val fraction = idx.toFloat() / cellCourses.size
                        val x = TIME_COL + cellW * ((course.day - 1) + fraction)
                        val w = cellW / cellCourses.size
                        val span = course.spanRows.coerceIn(1, times.size - course.row)
                        val y = rowH * course.row
                        val h = rowH * span
                        val accent = courseColor(course)
                        Box(
                            modifier = Modifier
                                .offset(x = x + 2.dp, y = y + 2.dp)
                                .size(width = w - 4.dp, height = h - 4.dp)
                                .background(
                                    color = accent.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .border(
                                    BorderStroke(1.dp, accent.copy(alpha = 0.75f)),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                        ) {
                            Column {
                                Text(
                                    text = course.courseName,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 6.sp,
                                        maxFontSize = 11.sp,
                                    ),
                                    maxLines = 6,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = autoFitStyle,
                                )
                                if (!course.room.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = course.room.orEmpty().take(5),
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = 7.sp,
                                            maxFontSize = 10.sp,
                                        ),
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = autoFitStyle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
