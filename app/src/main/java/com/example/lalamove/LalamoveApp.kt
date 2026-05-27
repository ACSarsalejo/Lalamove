package com.example.lalamove

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Application class — initialised once at startup before any Activity.
 *
 * Enables Firestore offline disk persistence so that Firestore documents
 * already fetched are served from the local cache on subsequent reads,
 * costing zero quota reads instead of one read per document per session.
 *
 * Disk cache limit: 10 MB. Documents evicted LRU when full.
 * Cache survives app restarts and process kills.
 */
class LalamoveApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(10L * 1024 * 1024)   // 10 MB cap
                        .build()
                )
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (_: Exception) {
            // Firestore settings can only be changed before the first use.
            // If already initialised (e.g. in tests), ignore silently.
        }
    }
}
