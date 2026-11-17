package com.codewave.qullqi.data.remote

import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.system.IdGenerator

/** This installation's identifier, created once (spec 004, FR-006). */
fun QullqiDatabase.deviceId(ids: IdGenerator): String {
    deviceQueries.ensureDevice(ids.newId())
    return deviceQueries.deviceId().executeAsOne()
}
