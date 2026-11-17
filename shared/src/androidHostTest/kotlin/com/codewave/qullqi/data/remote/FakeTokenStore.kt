package com.codewave.qullqi.data.remote

import com.codewave.qullqi.domain.model.UserId

/** Map-backed [TokenStore] for host tests. */
class FakeTokenStore : TokenStore {
    val values = mutableMapOf<String, String>()
    override fun refreshToken(userId: UserId) = values[userId.value]
    override fun saveRefreshToken(userId: UserId, token: String) { values[userId.value] = token }
    override fun clear(userId: UserId) { values.remove(userId.value) }
    override fun clearAll() = values.clear()
}
