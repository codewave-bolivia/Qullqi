package com.codewave.qullqi.data

import com.codewave.qullqi.data.remote.TokenStore
import com.codewave.qullqi.db.QullqiDatabase

/**
 * Debug only: clears everything stored on this device (cached accounts, session, preferences,
 * device id) and the renewal credentials, giving the "new device" state (spec 004, FR-021).
 * The app no longer ships sample accounts; the seed is used by tests only.
 */
class LocalDataReset(private val db: QullqiDatabase, private val tokens: TokenStore) {
    fun reset() {
        db.seedQueries.deleteAll()
        tokens.clearAll()
    }
}
