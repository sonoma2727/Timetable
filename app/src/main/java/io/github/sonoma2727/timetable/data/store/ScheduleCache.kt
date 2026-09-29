package io.github.sonoma2727.timetable.data.store

import android.content.Context
import io.github.sonoma2727.timetable.data.model.Schedule
import kotlinx.serialization.json.Json
import java.io.File

class ScheduleCache(context: Context) {

    private val file = File(context.filesDir, "schedule.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(): Schedule? = try {
        if (file.exists()) json.decodeFromString<Schedule>(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun write(schedule: Schedule) {
        file.writeText(json.encodeToString(Schedule.serializer(), schedule))
    }

    fun clear() {
        file.delete()
    }
}
