package io.github.sonoma2727.timetable.data.net

import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.Base64
import java.util.concurrent.TimeUnit

class JwxtApi {

    class LoginException(val userMessage: String) : Exception(userMessage)

    class SessionExpiredException : Exception("session expired")

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun login(account: String, password: String) {
        val (pwd, pwdstr1, pwdstr2) = encodePassword(password)
        val encoded = base64(account) + "%%%" + base64(pwd)
        val body = FormBody.Builder()
            .add("userAccount", account)
            .add("userPassword", "")
            .add("encoded", encoded)
            .add("pwdstr1", pwdstr1)
            .add("pwdstr2", pwdstr2)
            .build()
        val request = Request.Builder().url(LOGIN_URL).post(body).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val finalUrl = resp.request.url.toString()
            if (!resp.isSuccessful) {
                throw LoginException("教务系统返回 HTTP ${resp.code}")
            }
            if (!finalUrl.contains("xsMain") || !text.contains("userid")) {
                throw LoginException(extractError(text) ?: "登录失败，请检查学号和密码")
            }
        }
    }

    fun fetchScheduleHtml(): String {
        val request = Request.Builder().url(KB_URL).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("教务系统返回 HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            if (!text.contains("kbtable")) throw SessionExpiredException()
            return text
        }
    }

    private fun extractError(html: String): String? {
        Regex(
            """id=["']showMsg["'][^>]*>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).find(html)?.let { m ->
            val msg = m.groupValues[1].replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").trim()
            if (msg.isNotEmpty()) return msg
        }
        Regex("""alert\(['"]([^'"]+)['"]\)""").find(html)?.let { m ->
            val msg = m.groupValues[1].trim()
            if (!msg.contains("不能为空")) return msg
        }
        Regex(
            """<font[^>]*color=["']?red["']?[^>]*>([^<]{2,80})</font>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.let { m ->
            val msg = m.groupValues[1].trim()
            if ("温馨提示" !in msg && "IE" !in msg) return msg
        }
        return null
    }

    private fun encodePassword(password: String): Triple<String, String, String> {
        val out = password.toCharArray()
        val s1 = StringBuilder()
        val s2 = StringBuilder()
        for (i in password.indices) {
            when (password[i]) {
                '\u3002' -> {
                    out[i] = '.'
                    s1.append(i).append(',')
                }
                '\uff0c' -> {
                    out[i] = ','
                    s2.append(i).append(',')
                }
            }
        }
        return Triple(String(out), s1.toString(), s2.toString())
    }

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    companion object {
        const val BASE = "http://jwxt.cqrk.edu.cn:18080"
        const val LOGIN_URL = "$BASE/jsxsd/xk/LoginToXk"
        const val KB_URL = "$BASE/jsxsd/xskb/xskb_list.do?Ves632DSdyV=NEW_XSD_PYGL"
    }
}
