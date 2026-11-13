package com.codewave.qullqi

import android.app.Application
import android.content.pm.ApplicationInfo
import com.codewave.qullqi.data.AndroidDatabaseFactory
import com.codewave.qullqi.data.Pbkdf2PinHasher
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator
import com.codewave.qullqi.domain.system.UuidIdGenerator
import kotlinx.coroutines.Dispatchers

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
            hasher = Pbkdf2PinHasher(),
            clock = Clock { System.currentTimeMillis() },
            ids = UuidIdGenerator,
            otp = SampleOtpCodeGenerator,
            io = Dispatchers.IO,
            debug = debuggable,
        )
    }
}
