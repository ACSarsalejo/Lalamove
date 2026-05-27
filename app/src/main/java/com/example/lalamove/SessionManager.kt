package com.example.lalamove

import android.content.Context

object SessionManager {

    private const val PREFS    = "ll_session"
    private const val KEY_ROLE = "role"
    private const val KEY_UID  = "uid"        // Firebase Auth UID (String)
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL= "email"
    private const val KEY_PHONE= "phone"
    private const val KEY_VERIFIED = "is_verified"

    fun save(ctx: Context, result: LoginResult) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_ROLE,     result.role)
            putString(KEY_UID,      result.userId)   // Firebase UID
            putString(KEY_NAME,     result.name)
            putString(KEY_EMAIL,    result.email)
            putString(KEY_PHONE,    result.phone)
            putBoolean(KEY_VERIFIED,result.isVerified)
            apply()
        }
    }

    fun getRole(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, null)

    /** Returns the Firebase Auth UID (String). */
    fun getUserId(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UID, "") ?: ""

    /** Kept for compatibility — same as getUserId (Firebase UID = account ID). */
    fun getAcctId(ctx: Context): String = getUserId(ctx)

    fun getName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun getEmail(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""

    fun getPhone(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "") ?: ""

    fun isVerified(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VERIFIED, false)

    fun isLoggedIn(ctx: Context): Boolean = getRole(ctx) != null

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
