package com.codewave.qullqi

import android.app.Application
import android.content.pm.ApplicationInfo
import com.codewave.qullqi.data.AndroidDatabaseFactory
import com.codewave.qullqi.data.KeystoreTokenStore
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.UuidIdGenerator
import io.ktor.client.engine.okhttp.OkHttp
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.system.Today
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

/**
 * Builds the shared dependencies once per process so AuthActivity and MainActivity use the same
 * database, repositories and [com.codewave.qullqi.domain.session.AppLock].
 */
class QullqiApplication : Application() {

    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        dependencies = AppDependencies.create(
            db = AndroidDatabaseFactory.create(this),
            clock = Clock { System.currentTimeMillis() },
            ids = UuidIdGenerator,
            io = Dispatchers.IO,
            debug = debuggable,
            httpEngine = OkHttp.create(),
            baseUrl = BuildConfig.API_BASE_URL,
            tokens = KeystoreTokenStore(this),
            today = Today { LocalDate.now().let { SimpleDate(it.year, it.monthValue, it.dayOfMonth) } },
        )
    }
}
