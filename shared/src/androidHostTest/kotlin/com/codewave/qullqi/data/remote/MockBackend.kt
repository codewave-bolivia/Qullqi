package com.codewave.qullqi.data.remote

import com.codewave.qullqi.data.FakeClock
import com.codewave.qullqi.data.FakeIdGenerator
import com.codewave.qullqi.data.TestDb
import com.codewave.qullqi.data.testDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import java.io.IOException

/** A recorded request: path after the base URL, Authorization header and JSON body. */
data class Recorded(val path: String, val auth: String?, val body: String)

/** Scripted backend for host tests (contracts/api-mapping.md); no live server (constitution I). */
class MockBackend {
    val requests = mutableListOf<Recorded>()
    private val routes = mutableMapOf<String, ArrayDeque<Pair<Int, String>>>()
    var offline = false

    /** Answers [path] with this response from now on (replaces earlier ones). */
    fun on(path: String, status: Int, json: String) = sequence(path, status to json)

    /** Answers [path] with these responses in order; the last one repeats. */
    fun sequence(path: String, vararg responses: Pair<Int, String>) {
        routes[path] = ArrayDeque(responses.toList())
    }

    fun count(path: String) = requests.count { it.path == path }

    val engine = MockEngine { req ->
        val path = req.url.encodedPath.removePrefix("/api/v1/")
        requests += Recorded(path, req.headers[HttpHeaders.Authorization], String(req.body.toByteArray()))
        if (offline) throw IOException("offline")
        val queue = checkNotNull(routes[path]) { "no route for $path" }
        val (status, json) = if (queue.size > 1) queue.removeFirst() else queue.first()
        respond(json, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
    }

    companion object {
        fun ok(data: String) = """{"success":true,"message":"OK","status":200,"data":$data}"""
        fun error(code: String, details: String? = null) =
            """{"error":{"code":"$code","message":"x"${details?.let { ",\"details\":$it" } ?: ""}}}"""
        fun tokens(n: Int) = ok("""{"token":"access-$n","refreshToken":"refresh-$n","expiresIn":900}""")
        fun me(status: String = "VERIFIED", maternal: String? = "Quispe", idKey: String = "userId") = ok(
            """{"$idKey":"u-1","phoneNumber":"+59170000001","email":"Lucia@Example.com","firstName":"Lucía",
               "lastNamePaternal":"Flores"${maternal?.let { ",\"lastNameMaternal\":\"$it\"" } ?: ""},
               "verificationStatus":"$status","accountStatus":"ACTIVE"}""",
        )
    }
}

/** Remote repositories on an in-memory database and a [MockBackend]. */
class RemoteEnv {
    val backend = MockBackend()
    val t: TestDb = testDatabase(withSamples = false)
    val tokens = FakeTokenStore()
    val api = QullqiApi(backend.engine, "http://test/api/v1/", debug = false)
    private val ids = FakeIdGenerator()
    val deviceId: () -> String = { t.db.deviceId(ids) }
    val session = RemoteSessionRepository(t.db, api, tokens, deviceId, FakeClock(), Dispatchers.Unconfined)
    val registration = RemoteRegistrationRepository(api, session, tokens, deviceId)
}
