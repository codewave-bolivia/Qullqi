package com.codewave.qullqi.data

import com.codewave.qullqi.db.QullqiDatabase

/**
 * Restores the local data (spec 002, FR-022, FR-024). `withSamples = false` gives the
 * "empty device" state used to demo the welcome and registration flow.
 */
class LocalDataReset(private val db: QullqiDatabase) {
    fun reset(withSamples: Boolean) {
        db.transaction {
            db.seedQueries.deleteAll()
            if (withSamples) db.seedQueries.insertSamples()
        }
    }
}
