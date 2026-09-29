package io.github.sonoma2727.timetable.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sonoma2727.timetable.data.ScheduleRepository
import io.github.sonoma2727.timetable.data.model.Schedule
import io.github.sonoma2727.timetable.data.net.JwxtApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

data class UiState(
    val initialized: Boolean = false,
    val needLogin: Boolean = false,
    val schedule: Schedule? = null,
    val loading: Boolean = false,
    val loginError: String? = null,
    val notice: String? = null,
    val account: String = "",
    val firstWeekMonday: Long? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ScheduleRepository(application)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    @Volatile
    private var refreshInFlight = false

    init {
        viewModelScope.launch {
            val cached = repo.cached()
            val creds = repo.currentCredentials()
            val firstMonday = repo.currentFirstWeekMonday()
            cached?.let { installCourseColors(it.courses) }
            _state.update {
                it.copy(
                    initialized = true,
                    schedule = cached,
                    account = creds?.account ?: "",
                    firstWeekMonday = firstMonday,
                    needLogin = creds == null,
                )
            }
            if (creds != null) refresh(silent = true)
        }
    }

    fun login(account: String, password: String, remember: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loginError = null) }
            try {
                repo.login(account, password)
            } catch (e: Exception) {
                handleError(e, fromLogin = true)
                return@launch
            }
            try {
                repo.saveCredentials(account, password, remember)
            } catch (t: Throwable) {
                Log.e(TAG, "saveCredentials failed, continuing without persistence", t)
            }
            val firstMonday = try {
                repo.currentFirstWeekMonday()
            } catch (t: Throwable) {
                Log.e(TAG, "read settings failed", t)
                null
            }
            _state.update {
                it.copy(
                    needLogin = false,
                    account = if (remember) account else "",
                    loginError = null,
                    firstWeekMonday = firstMonday,
                )
            }
            refresh()
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            try {
                if (!silent) _state.update { it.copy(loading = true) }
                val schedule = repo.refresh()
                installCourseColors(schedule.courses)
                _state.update {
                    it.copy(
                        schedule = schedule,
                        loading = false,
                        notice = if (silent) null else "课表已更新",
                    )
                }
            } catch (e: Exception) {
                handleError(e, fromLogin = false)
            } finally {
                refreshInFlight = false
            }
        }
    }

    fun refreshIfStale() {
        val s = _state.value
        if (s.needLogin || s.loading) return
        val fetchedAt = s.schedule?.fetchedAt ?: 0L
        if (System.currentTimeMillis() - fetchedAt >= STALE_THRESHOLD_MS) {
            refresh(silent = true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            val keepMonday = _state.value.firstWeekMonday
            repo.logout()
            _state.value = UiState(
                initialized = true,
                needLogin = true,
                firstWeekMonday = keepMonday,
            )
        }
    }

    fun setFirstWeekMonday(dateMillis: Long) {
        viewModelScope.launch {
            val epochDay = dateMillis / MILLIS_PER_DAY
            val monday = LocalDate.ofEpochDay(epochDay).with(DayOfWeek.MONDAY)
            repo.setFirstWeekMonday(monday.toEpochDay())
            _state.update { it.copy(firstWeekMonday = monday.toEpochDay()) }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun handleError(e: Exception, fromLogin: Boolean) {
        Log.e(TAG, "handled error (fromLogin=$fromLogin)", e)
        val message = when (e) {
            is JwxtApi.LoginException -> e.userMessage
            is ScheduleRepository.NotLoggedInException -> "请先登录教务系统"
            is JwxtApi.SessionExpiredException -> "登录已过期，请重新登录"
            is IOException -> "网络异常，请检查网络连接"
            else -> e.message?.takeIf { it.isNotBlank() }
                ?: "出错了，请稍后重试（${e.javaClass.simpleName}）"
        }
        _state.update { s ->
            val needsLogin = e is JwxtApi.LoginException ||
                e is ScheduleRepository.NotLoggedInException ||
                e is JwxtApi.SessionExpiredException
            s.copy(
                loading = false,
                loginError = if (fromLogin) message else s.loginError,
                notice = if (fromLogin) null else message,
                needLogin = if (!fromLogin && needsLogin && s.schedule == null) true else s.needLogin,
            )
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val STALE_THRESHOLD_MS = 600_000L
    }
}
