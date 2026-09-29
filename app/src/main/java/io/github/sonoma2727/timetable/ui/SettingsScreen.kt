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
                "1. 本项目为个人学习用途的第三方工具，与学校及其教务系统官方无关，非官方产品。\n" +
                "2. 仅限使用者本人账号自用；禁止用于查询他人数据、批量抓取或任何商业用途。\n" +
                "3. 使用者应遵守学校规章制度与国家法律法规，因违规使用产生的后果自行承担。\n" +
                "4. 课表数据实时来源于教务系统，可能存在滞后或偏差，一切以教务系统及学校正式发布为准。\n" +
                "5. 账号凭据仅加密保存在本机（Android Keystore），本软件不收集、不上传任何数据；" +
                "因本机失陷（Root、恶意软件等）导致的凭据风险由使用者自担。\n" +
                "6. 教务系统升级或接口变更可能导致本软件失效，不保证持续可用。\n" +
                "7. 本软件按“现状”提供，不作任何明示或暗示担保；" +
                "对因使用产生的直接或间接损失，开发者不承担责任。",
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
