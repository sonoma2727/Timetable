package io.github.sonoma2727.timetable.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sonoma2727.timetable.data.Weeks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onLogout: () -> Unit,
    onPickDate: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val schedule = state.schedule
    val currentWeek = state.firstWeekMonday?.let { Weeks.currentWeek(it) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle("教务信息")
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("学期", schedule?.weekInfo?.term ?: "—")
                InfoRow("时间模板", schedule?.weekInfo?.timeTemplate ?: "—")
                InfoRow(
                    "数据更新",
                    schedule?.fetchedAt
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter) }
                        ?: "—",
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("开学日期")
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("第一周的周一", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.firstWeekMonday
                                ?.let { LocalDate.ofEpochDay(it).format(dateFormatter) }
                                ?: "未设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.firstWeekMonday == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { showPicker = true }) { Text("选择日期") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (currentWeek != null) "用于计算当前周次，当前为第 $currentWeek 周"
                    else "用于计算当前周次，未设置前课表不做周次过滤",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("账号")
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("当前账号", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.account.ifBlank { "未记住账号" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onLogout) { Text("退出登录") }
            }
        }

        Spacer(Modifier.height(32.dp))
        val context = LocalContext.current
        Text(
            text = "课表 v0.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "免责声明：\n" +
                "1. 本应用为个人学习用途的开源工具，非学校或教务系统官方产品，" +
                "与校方及教务系统运营方无任何关联、授权或背书；\n" +
                "2. 课表数据来自教务系统，课程的时间、地点、教师等信息仅供参考，" +
                "以教务系统及学校官方通知为准；\n" +
                "3. 本应用按“现状”提供，不对其准确性、完整性、及时性、可用性" +
                "作任何明示或暗示的保证；\n" +
                "4. 仅限本人账号使用，请勿用于获取、查询他人数据，" +
                "不得用于商业用途或任何违法违规活动；\n" +
                "5. 因使用或无法使用本应用造成的任何直接或间接损失" +
                "（包括但不限于漏课、调课未同步、课程时间冲突等），" +
                "开发者在法律允许的最大范围内不承担责任，后果由使用者自行承担；\n" +
                "6. 账号密码经加密后仅保存在本机，不上传至任何第三方服务器；" +
                "因设备被 Root、越狱或恶意软件入侵导致的凭据泄露，风险由使用者自行承担；\n" +
                "7. 使用者应遵守学校规章制度与国家法律法规，" +
                "因违规使用本应用产生的一切后果由使用者自行承担。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        Text(
            text = "项目地址：github.com/sonoma2727/Timetable",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/sonoma2727/Timetable"),
                    )
                    context.startActivity(intent)
                },
        )
    }

    if (showPicker) {
        val initialMillis = (state.firstWeekMonday ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(onPickDate)
                        showPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
