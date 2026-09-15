package com.snp.bookstore.data.source.local

import android.content.Context
import androidx.core.content.edit
import net.openid.appauth.AuthState

/**
 * Lưu AuthState (access/refresh/id token) trong SharedPreferences.
 * Demo only — production nên dùng EncryptedSharedPreferences.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_state", Context.MODE_PRIVATE)

    fun save(state: AuthState) {
        prefs.edit { putString(KEY, state.jsonSerializeString()) }
    }

    fun read(): AuthState? {
        val json = prefs.getString(KEY, null) ?: return null
        return AuthState.jsonDeserialize(json)
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    companion object {
        private const val KEY = "auth_state"
    }
}
