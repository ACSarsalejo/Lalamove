package com.example.lalamove

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight TTL cache backed by SharedPreferences.
 *
 * Reduces Firebase quota usage by serving repeated reads from local storage
 * instead of Firestore. Default TTL is 5 minutes. Writes and mutations
 * always go directly to the data source — only reads are cached.
 *
 * Usage:
 *   // Read (returns null if stale/missing)
 *   val cached = FirestoreCache.get(ctx, "wallet_balance_$uid")
 *   if (cached != null) { /* use cached */ } else { /* fetch fresh */ }
 *
 *   // Write
 *   FirestoreCache.put(ctx, "wallet_balance_$uid", jsonString)
 *
 *   // Invalidate after a mutation (topup, deduction, etc.)
 *   FirestoreCache.invalidate(ctx, "wallet_balance_$uid")
 *   FirestoreCache.invalidate(ctx, "wallet_txns_$uid")
 */
object FirestoreCache {

    private const val PREFS_NAME     = "ll_fs_cache"
    const val TTL_SHORT_MS  = 2 * 60 * 1000L   // 2 min  — balance (changes after transactions)
    const val TTL_DEFAULT_MS = 5 * 60 * 1000L   // 5 min  — transactions, profile
    const val TTL_LONG_MS   = 30 * 60 * 1000L   // 30 min — addresses, coupons (rarely change)

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Store a JSON string under [key] with the current timestamp. */
    fun put(ctx: Context, key: String, json: String) {
        prefs(ctx).edit()
            .putString(key, json)
            .putLong("${key}_ts", System.currentTimeMillis())
            .apply()
    }

    /**
     * Return the cached JSON string if it was stored within [ttlMs] ms ago.
     * Returns null when missing or stale — caller must fetch fresh data.
     */
    fun get(ctx: Context, key: String, ttlMs: Long = TTL_DEFAULT_MS): String? {
        val ts = prefs(ctx).getLong("${key}_ts", 0L)
        if (System.currentTimeMillis() - ts > ttlMs) return null
        return prefs(ctx).getString(key, null)
    }

    /** Invalidate a single entry — call after any mutation that changes the data. */
    fun invalidate(ctx: Context, key: String) {
        prefs(ctx).edit()
            .remove(key)
            .remove("${key}_ts")
            .apply()
    }

    /** Invalidate all entries for a user (e.g. on logout). */
    fun invalidateAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
