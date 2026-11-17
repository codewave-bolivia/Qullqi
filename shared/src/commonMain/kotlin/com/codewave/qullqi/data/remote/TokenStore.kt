package com.codewave.qullqi.data.remote

import com.codewave.qullqi.domain.model.UserId

/**
 * Renewal credentials per account, kept only in platform secure storage (spec 004, FR-005).
 * The short-lived access token is never stored: it lives in memory in [QullqiApi].
 */
interface TokenStore {
    fun refreshToken(userId: UserId): String?
    fun saveRefreshToken(userId: UserId, token: String)
    fun clear(userId: UserId)
    fun clearAll()
}
