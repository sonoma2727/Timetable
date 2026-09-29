package io.github.sonoma2727.timetable.data

import android.content.Context
import io.github.sonoma2727.timetable.data.model.Schedule
import io.github.sonoma2727.timetable.data.net.JwxtApi
import io.github.sonoma2727.timetable.data.net.ScheduleParser
import io.github.sonoma2727.timetable.data.store.Credentials
import io.github.sonoma2727.timetable.data.store.CredentialsStore
import io.github.sonoma2727.timetable.data.store.ScheduleCache
import io.github.sonoma2727.timetable.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ScheduleRepository(context: Context) {

    class NotLoggedInException : Exception("尚未登录教务系统")

    private val api = JwxtApi()
    private val cache = ScheduleCache(context)
    private val credentialsStore = CredentialsStore(context)
    private val settingsStore = SettingsStore(context)

    @Volatile
    private var sessionAlive = false

    val credentials: Flow<Credentials?> = credentialsStore.credentials
    val firstWeekMonday: Flow<Long?> = settingsStore.firstWeekMonday

    fun cached(): Schedule? = cache.read()

    suspend fun currentCredentials(): Credentials? = credentialsStore.current()

    suspend fun login(account: String, password: String) {
        withContext(Dispatchers.IO) {
            api.login(account, password)
            sessionAlive = true
        }
    }

    suspend fun saveCredentials(account: String, password: String, remember: Boolean) {
        credentialsStore.save(account, password, remember)
    }

    suspend fun refresh(): Schedule = withContext(Dispatchers.IO) {
        try {
            fetchOnce()
        } catch (e: JwxtApi.SessionExpiredException) {
            sessionAlive = false
            fetchOnce()
        }
    }

    private suspend fun fetchOnce(): Schedule {
        ensureSession()
        val html = try {
            api.fetchScheduleHtml()
        } catch (e: JwxtApi.SessionExpiredException) {
            sessionAlive = false
            ensureSession()
            api.fetchScheduleHtml()
        }
        val weekInfo = ScheduleParser.extractWeekInfo(html)
        val table = ScheduleParser.parseTable(html)
        val schedule = Schedule(
            source = JwxtApi.BASE,
            fetchedAt = System.currentTimeMillis(),
            weekInfo = weekInfo,
            days = table.days,
            times = table.times,
            courses = table.courses,
        )
        cache.write(schedule)
        return schedule
    }

    private suspend fun ensureSession() {
        if (sessionAlive) return
        val creds = credentialsStore.current() ?: throw NotLoggedInException()
        api.login(creds.account, creds.password)
        sessionAlive = true
    }

    suspend fun logout() {
        credentialsStore.clear()
        cache.clear()
        sessionAlive = false
    }

    suspend fun setFirstWeekMonday(epochDay: Long) {
        settingsStore.setFirstWeekMonday(epochDay)
    }

    suspend fun currentFirstWeekMonday(): Long? = settingsStore.currentFirstWeekMonday()
}
