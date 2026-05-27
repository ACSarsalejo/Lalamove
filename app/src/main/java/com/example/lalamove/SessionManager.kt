package com.example.lalamove

import android.content.Context

object SessionManager {

    private const val PREFS = "ll_session"
    private const val KEY_ROLE    = "role"
    private const val KEY_ACCT_ID = "acct_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME    = "name"
    private const val KEY_EMAIL   = "email"
    private const val KEY_PHONE   = "phone"

    fun save(ctx: Context, result: LoginResult) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_ROLE,    result.role)
            putInt   (KEY_ACCT_ID, result.acctId)
            putLong  (KEY_USER_ID, result.userId)
            putString(KEY_NAME,    result.name)
            putString(KEY_EMAIL,   result.email)
            putString(KEY_PHONE,   result.phone)
            apply()
        }
    }

    fun getRole(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, null)

    fun getAcctId(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACCT_ID, 0)

    fun getUserId(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_USER_ID, 0L)

    fun getName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun getEmail(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""

    fun getPhone(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "") ?: ""

    fun isLoggedIn(ctx: Context): Boolean = getRole(ctx) != null

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
