package com.example.lalamove

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(
    val role: String,
    val userId: String,     // Firebase Auth UID
    val name: String,
    val email: String,
    val phone: String,
    val isVerified: Boolean
)

object ApiClient {

    private val auth      get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()
    private val storage   get() = FirebaseStorage.getInstance()
    private val handler   = Handler(Looper.getMainLooper())

    // ── Auth ────────────────────────────────────────────────────────────────

    fun login(identifier: String, password: String, callback: (LoginResult?, String) -> Unit) {
        // Firebase Auth only accepts email. If identifier looks like a phone number,
        // look up the matching email from Firestore first, then sign in.
        val looksLikePhone = identifier.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                             && identifier.any { it.isDigit() }
                             && !identifier.contains("@")
        if (looksLikePhone) {
            // Search customer collection first, then driver
            val phone = identifier.trim()
            firestore.collection("customer").whereEqualTo("Cust_Phone", phone).limit(1).get()
                .addOnSuccessListener { custSnap ->
                    val email = custSnap.documents.firstOrNull()?.getString("Cust_Email")
                    if (email != null) {
                        login(email, password, callback)
                    } else {
                        firestore.collection("driver").whereEqualTo("Drvr_PhoneNum", phone).limit(1).get()
                            .addOnSuccessListener { drvrSnap ->
                                val drvrEmail = drvrSnap.documents.firstOrNull()?.getString("Drvr_Email")
                                if (drvrEmail != null) {
                                    login(drvrEmail, password, callback)
                                } else {
                                    handler.post { callback(null, "No account found with this phone number.") }
                                }
                            }
                            .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed.") } }
                    }
                }
                .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed.") } }
            return
        }

        auth.signInWithEmailAndPassword(identifier, password)
            .addOnSuccessListener { authResult ->
                val firebaseUid = authResult.user!!.uid
                // Step 1: look up the custom ID from the uid_map collection
                firestore.collection("uid_map").document(firebaseUid).get()
                    .addOnSuccessListener { mapDoc ->
                        if (mapDoc.exists()) {
                            val docId = mapDoc.getString("customId") ?: ""
                            val role  = mapDoc.getString("role") ?: "customer"
                            val col   = if (role == "driver") "driver" else "customer"
                            // Step 2: fetch the actual profile using the custom ID as doc ID
                            firestore.collection(col).document(docId).get()
                                .addOnSuccessListener { profileDoc ->
                                    if (!profileDoc.exists()) {
                                        handler.post { callback(null, "Profile not found. Please contact support.") }
                                        return@addOnSuccessListener
                                    }
                                    val result = if (role == "driver") {
                                        LoginResult(
                                            role       = "driver",
                                            userId     = docId,
                                            name       = "${profileDoc.getString("Drvr_FirstName") ?: ""} ${profileDoc.getString("Drvr_LastName") ?: ""}".trim(),
                                            email      = profileDoc.getString("Drvr_Email") ?: identifier,
                                            phone      = profileDoc.getString("Drvr_PhoneNum") ?: "",
                                            isVerified = profileDoc.getBoolean("Drvr_IsVerified") ?: false
                                        )
                                    } else {
                                        LoginResult(
                                            role       = "customer",
                                            userId     = docId,
                                            name       = "${profileDoc.getString("Cust_FirstName") ?: ""} ${profileDoc.getString("Cust_LastName") ?: ""}".trim(),
                                            email      = profileDoc.getString("Cust_Email") ?: identifier,
                                            phone      = profileDoc.getString("Cust_Phone") ?: "",
                                            isVerified = false
                                        )
                                    }
                                    handler.post { callback(result, "") }
                                }
                                .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
                        } else {
                            // Fallback: account pre-dates the uid_map (doc ID = Firebase UID)
                            loginLegacyByUid(firebaseUid, identifier, callback)
                        }
                    }
                    .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
            }
            .addOnFailureListener { e ->
                val msg = when (e) {
                    is FirebaseAuthInvalidUserException         -> "No account found with this email."
                    is FirebaseAuthInvalidCredentialsException -> "Incorrect password."
                    else                                       -> e.message ?: "Login failed."
                }
                handler.post { callback(null, msg) }
            }
    }

    /**
     * Fallback for accounts that have no uid_map entry yet.
     * Tries doc ID = Firebase UID first (oldest accounts), then queries by
     * Cust_FirebaseUID / Drvr_FirebaseUID field (accounts created after custom-ID migration
     * but before uid_map was introduced). On success, also writes the uid_map entry
     * so future logins go through the fast path.
     */
    private fun loginLegacyByUid(uid: String, identifier: String, callback: (LoginResult?, String) -> Unit) {
        // ── customer: try doc-id = uid ──────────────────────────────────────
        firestore.collection("customer").document(uid).get()
            .addOnSuccessListener { custDoc ->
                if (custDoc.exists()) {
                    val docId = custDoc.id
                    backfillUidMap(uid, docId, "customer")
                    handler.post {
                        callback(LoginResult(
                            role = "customer", userId = docId,
                            name  = "${custDoc.getString("Cust_FirstName") ?: ""} ${custDoc.getString("Cust_LastName") ?: ""}".trim(),
                            email = custDoc.getString("Cust_Email") ?: identifier,
                            phone = custDoc.getString("Cust_Phone") ?: "",
                            isVerified = false
                        ), "")
                    }
                } else {
                    // ── customer: try query by Cust_FirebaseUID field ───────
                    firestore.collection("customer").whereEqualTo("Cust_FirebaseUID", uid).limit(1).get()
                        .addOnSuccessListener { custSnap ->
                            val custQDoc = custSnap.documents.firstOrNull()
                            if (custQDoc != null) {
                                val docId = custQDoc.id
                                backfillUidMap(uid, docId, "customer")
                                handler.post {
                                    callback(LoginResult(
                                        role = "customer", userId = docId,
                                        name  = "${custQDoc.getString("Cust_FirstName") ?: ""} ${custQDoc.getString("Cust_LastName") ?: ""}".trim(),
                                        email = custQDoc.getString("Cust_Email") ?: identifier,
                                        phone = custQDoc.getString("Cust_Phone") ?: "",
                                        isVerified = false
                                    ), "")
                                }
                            } else {
                                // ── driver: try doc-id = uid ───────────────
                                firestore.collection("driver").document(uid).get()
                                    .addOnSuccessListener { drvrDoc ->
                                        if (drvrDoc.exists()) {
                                            val docId = drvrDoc.id
                                            backfillUidMap(uid, docId, "driver")
                                            handler.post {
                                                callback(LoginResult(
                                                    role = "driver", userId = docId,
                                                    name  = "${drvrDoc.getString("Drvr_FirstName") ?: ""} ${drvrDoc.getString("Drvr_LastName") ?: ""}".trim(),
                                                    email = drvrDoc.getString("Drvr_Email") ?: identifier,
                                                    phone = drvrDoc.getString("Drvr_PhoneNum") ?: "",
                                                    isVerified = drvrDoc.getBoolean("Drvr_IsVerified") ?: false
                                                ), "")
                                            }
                                        } else {
                                            // ── driver: try query by Drvr_FirebaseUID field ──
                                            firestore.collection("driver").whereEqualTo("Drvr_FirebaseUID", uid).limit(1).get()
                                                .addOnSuccessListener { drvrSnap ->
                                                    val drvrQDoc = drvrSnap.documents.firstOrNull()
                                                    if (drvrQDoc != null) {
                                                        val docId = drvrQDoc.id
                                                        backfillUidMap(uid, docId, "driver")
                                                        handler.post {
                                                            callback(LoginResult(
                                                                role = "driver", userId = docId,
                                                                name  = "${drvrQDoc.getString("Drvr_FirstName") ?: ""} ${drvrQDoc.getString("Drvr_LastName") ?: ""}".trim(),
                                                                email = drvrQDoc.getString("Drvr_Email") ?: identifier,
                                                                phone = drvrQDoc.getString("Drvr_PhoneNum") ?: "",
                                                                isVerified = drvrQDoc.getBoolean("Drvr_IsVerified") ?: false
                                                            ), "")
                                                        }
                                                    } else {
                                                        handler.post { callback(null, "Account not found. Please sign up.") }
                                                    }
                                                }
                                                .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
                                        }
                                    }
                                    .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
                            }
                        }
                        .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
                }
            }
            .addOnFailureListener { e -> handler.post { callback(null, e.message ?: "Login failed") } }
    }

    /** Writes a uid_map entry so future logins skip the legacy path. */
    private fun backfillUidMap(firebaseUid: String, customId: String, role: String) {
        firestore.collection("uid_map").document(firebaseUid)
            .set(mapOf("customId" to customId, "role" to role))
    }

    /**
     * Generates a custom display ID matching the website format:
     * [rolePrefix 1-digit][sequence 4-digit padded][month no-leading-zero][day 2-digit][year 2-digit]
     * e.g. 1000151826 = customer #0001, May 18, 2026
     *
     * Sequence = current document count in the collection + 1.
     * No counter document is needed — the count is derived from the collection itself.
     */
    private fun generateCustomId(
        rolePrefix: Int,
        collection: String,   // "customer" or "driver"
        callback: (Long) -> Unit
    ) {
        firestore.collection(collection).count().get(AggregateSource.SERVER)
            .addOnSuccessListener { result ->
                val seq  = result.count + 1L
                val cal  = java.util.Calendar.getInstance()
                val mon  = cal.get(java.util.Calendar.MONTH) + 1   // 1–12, no leading zero (matches PHP date('n'))
                val day  = String.format("%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
                val yr   = String.format("%02d", cal.get(java.util.Calendar.YEAR) % 100)
                val id   = "$rolePrefix${String.format("%04d", seq)}$mon$day$yr".toLong()
                callback(id)
            }
            .addOnFailureListener {
                // Fallback: timestamp-derived (unique enough for a small app)
                val cal  = java.util.Calendar.getInstance()
                val mon  = cal.get(java.util.Calendar.MONTH) + 1
                val day  = String.format("%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
                val yr   = String.format("%02d", cal.get(java.util.Calendar.YEAR) % 100)
                val seq  = (System.currentTimeMillis() % 9999 + 1)
                val id   = "$rolePrefix${String.format("%04d", seq)}$mon$day$yr".toLong()
                callback(id)
            }
    }

    fun register(
        firstName: String, lastName: String,
        phone: String, email: String, password: String,
        callback: (Boolean, String, String) -> Unit   // success, uid, error
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user!!.uid
                // Generate custom display ID matching website format
                generateCustomId(rolePrefix = 1, collection = "customer") { customId ->
                    val now = com.google.firebase.Timestamp.now()
                    val custData = mapOf(
                        "Cust_ID"             to customId,
                        "Cust_FirebaseUID"    to uid,
                        "Cust_FirstName"      to firstName,
                        "Cust_LastName"       to lastName,
                        "Cust_Email"          to email,
                        "Cust_Phone"          to phone,
                        "Cust_WalletBalance"  to 0.0,
                        "Cust_IsBanned"       to false,
                        "Cust_CreatedAt"      to now
                    )
                    val acctData = mapOf(
                        "Acct_Id"          to customId,
                        "Acct_FirebaseUID" to uid,
                        "Acct_Email"       to email,
                        "Acct_Phone"       to phone,
                        "Acct_Role"        to "customer",
                        "Acct_IsSuspended" to false,
                        "Acct_CreatedAt"   to now
                    )
                    val docId = customId.toString()
                    val batch = firestore.batch()
                    batch.set(firestore.collection("customer").document(docId), custData, SetOptions.merge())
                    batch.set(firestore.collection("accounts").document(docId), acctData, SetOptions.merge())
                    batch.set(firestore.collection("uid_map").document(uid),
                        mapOf("customId" to docId, "role" to "customer"))
                    batch.commit()
                        .addOnSuccessListener { handler.post { callback(true, docId, "") } }
                        .addOnFailureListener { e -> handler.post { callback(false, "", e.message ?: "Profile write failed") } }
                }
            }
            .addOnFailureListener { e ->
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException   -> "An account with this email already exists."
                    is FirebaseAuthWeakPasswordException    -> "Password must be at least 6 characters."
                    else                                    -> e.message ?: "Registration failed."
                }
                handler.post { callback(false, "", msg) }
            }
    }

    fun registerDriver(
        firstName: String, lastName: String,
        phone: String, email: String, password: String, vehicleType: String,
        callback: (Boolean, String, String) -> Unit   // success, uid, error
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user!!.uid
                // Generate custom display ID matching website format (prefix 2 = driver)
                generateCustomId(rolePrefix = 2, collection = "driver") { customId ->
                    val now = com.google.firebase.Timestamp.now()
                    val drvrData = mapOf(
                        "Drvr_ID"             to customId,
                        "Drvr_FirebaseUID"    to uid,
                        "Drvr_FirstName"      to firstName,
                        "Drvr_LastName"       to lastName,
                        "Drvr_Email"          to email,
                        "Drvr_PhoneNum"       to phone,
                        "Drvr_VhclTypeID"     to vehicleType,
                        "Drvr_LicenseNum"     to "TEMP-$uid",
                        "Drvr_IsVerified"     to false,
                        "Drvr_IsRejected"     to false,
                        "Drvr_IsSuspended"    to false,
                        "Drvr_Status"         to "offline",
                        "Drvr_Rating"         to 0.0,
                        "Drvr_RatingCount"    to 0,
                        "Drvr_WalletBalance"  to 0.0,
                        "Drvr_CommissionRate" to 0.80,
                        "Drvr_AreaID"         to 1,
                        "Drvr_IdentityStatus" to "pending",
                        "Drvr_VehicleStatus"  to "pending",
                        "Drvr_CreatedAt"      to now
                    )
                    val acctData = mapOf(
                        "Acct_Id"          to customId,
                        "Acct_FirebaseUID" to uid,
                        "Acct_Email"       to email,
                        "Acct_Phone"       to phone,
                        "Acct_Role"        to "driver",
                        "Acct_IsSuspended" to false,
                        "Acct_CreatedAt"   to now
                    )
                    val docId = customId.toString()
                    val batch = firestore.batch()
                    batch.set(firestore.collection("driver").document(docId), drvrData, SetOptions.merge())
                    batch.set(firestore.collection("accounts").document(docId), acctData, SetOptions.merge())
                    batch.set(firestore.collection("uid_map").document(uid),
                        mapOf("customId" to docId, "role" to "driver"))
                    batch.commit()
                        .addOnSuccessListener { handler.post { callback(true, docId, "") } }
                        .addOnFailureListener { e -> handler.post { callback(false, "", e.message ?: "Profile write failed") } }
                }
            }
            .addOnFailureListener { e ->
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException   -> "An account with this email already exists."
                    is FirebaseAuthWeakPasswordException    -> "Password must be at least 6 characters."
                    else                                    -> e.message ?: "Registration failed."
                }
                handler.post { callback(false, "", msg) }
            }
    }

    /** Sends a Firebase password reset email. No XAMPP needed. */
    fun forgotPassword(email: String, callback: (Boolean, String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { handler.post { callback(true, "Reset email sent! Check your inbox.") } }
            .addOnFailureListener { e ->
                val msg = when (e) {
                    is FirebaseAuthInvalidUserException -> "No account found with this email."
                    else -> e.message ?: "Failed to send reset email."
                }
                handler.post { callback(false, msg) }
            }
    }

    fun changePassword(newPw: String, callback: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) { callback(false, "Not logged in."); return }
        user.updatePassword(newPw)
            .addOnSuccessListener { handler.post { callback(true, "") } }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Password change failed.") } }
    }

    // ── Profile ─────────────────────────────────────────────────────────────

    fun getProfile(uid: String, role: String, callback: (JSONObject?) -> Unit) {
        val col = if (role == "driver") "driver" else "customer"
        firestore.collection(col).document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) handler.post { callback(JSONObject(doc.data ?: emptyMap<String, Any>())) }
                else handler.post { callback(null) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    fun updateProfile(uid: String, role: String, firstName: String, lastName: String, phone: String, email: String, callback: (Boolean, String) -> Unit) {
        val col = if (role == "driver") "driver" else "customer"
        val updates = if (role == "driver") mapOf("Drvr_FirstName" to firstName, "Drvr_LastName" to lastName, "Drvr_PhoneNum" to phone, "Drvr_Email" to email)
                      else mapOf("Cust_FirstName" to firstName, "Cust_LastName" to lastName, "Cust_Phone" to phone, "Cust_Email" to email)
        firestore.collection(col).document(uid).update(updates)
            .addOnSuccessListener { handler.post { callback(true, "") } }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Update failed.") } }
    }

    fun updatePhoto(uid: String, role: String, photoBytes: ByteArray, mimeType: String, callback: (Boolean, String) -> Unit) {
        val ext = if (mimeType.contains("png")) "png" else "jpg"
        val ref = storage.reference.child("photos/$uid/profile.$ext")
        ref.putBytes(photoBytes)
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { uri ->
                val col = if (role == "driver") "driver" else "customer"
                val field = if (role == "driver") "Drvr_ProfilePhoto" else "Cust_ProfilePhoto"
                firestore.collection(col).document(uid).update(field, uri.toString())
                handler.post { callback(true, uri.toString()) }
            }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Upload failed.") } }
    }

    // ── Addresses ────────────────────────────────────────────────────────────

    fun getAddresses(uid: String, callback: (JSONArray?) -> Unit) {
        firestore.collection("customer").document(uid).collection("addresses")
            .get()
            .addOnSuccessListener { snap ->
                val arr = JSONArray()
                snap.documents.forEach { d ->
                    arr.put(JSONObject(d.data ?: emptyMap<String, Any>()).apply { put("addr_id", d.id) })
                }
                handler.post { callback(arr) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    fun addAddress(uid: String, label: String, address: String, type: String, callback: (Boolean, String) -> Unit) {
        val data = mapOf("label" to label, "address" to address, "type" to type)
        firestore.collection("customer").document(uid).collection("addresses")
            .add(data)
            .addOnSuccessListener { ref -> handler.post { callback(true, ref.id) } }
            .addOnFailureListener { handler.post { callback(false, "") } }
    }

    fun updateAddress(uid: String, addrId: String, label: String, address: String, type: String, callback: (Boolean) -> Unit) {
        firestore.collection("customer").document(uid).collection("addresses").document(addrId)
            .update(mapOf("label" to label, "address" to address, "type" to type))
            .addOnSuccessListener { handler.post { callback(true) } }
            .addOnFailureListener { handler.post { callback(false) } }
    }

    fun deleteAddress(uid: String, addrId: String, callback: (Boolean) -> Unit) {
        firestore.collection("customer").document(uid).collection("addresses").document(addrId)
            .delete()
            .addOnSuccessListener { handler.post { callback(true) } }
            .addOnFailureListener { handler.post { callback(false) } }
    }

    // ── Wallet ───────────────────────────────────────────────────────────────

    fun walletBalance(uid: String, role: String, callback: (Double?) -> Unit) {
        val col   = if (role == "driver") "driver" else "customer"
        val field = if (role == "driver") "Drvr_WalletBalance" else "Cust_WalletBalance"
        firestore.collection(col).document(uid).get()
            .addOnSuccessListener { doc -> handler.post { callback(doc.getDouble(field)) } }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    fun walletTopUp(uid: String, role: String, amount: Double, method: String = "GCash", callback: (Boolean, Double, String) -> Unit) {
        val col   = if (role == "driver") "driver" else "customer"
        val field = if (role == "driver") "Drvr_WalletBalance" else "Cust_WalletBalance"
        val ref   = firestore.collection(col).document(uid)
        firestore.runTransaction { tx ->
            val snap    = tx.get(ref)
            val current = snap.getDouble(field) ?: 0.0
            val newBal  = current + amount
            tx.update(ref, field, newBal)
            newBal
        }.addOnSuccessListener { newBal ->
            val ref2  = "TOPUP-${System.currentTimeMillis()}"
            val txData = mapOf(
                "type"        to "topup",
                "amount"      to amount,
                "description" to "Top-up via ${method.uppercase()}",
                "reference"   to ref2,
                "date"        to com.google.firebase.Timestamp.now()
            )
            firestore.collection(col).document(uid).collection("transactions").add(txData)
            handler.post { callback(true, newBal, ref2) }
        }.addOnFailureListener { e -> handler.post { callback(false, 0.0, e.message ?: "Top-up failed.") } }
    }

    fun walletDeduct(uid: String, role: String, amount: Double, description: String, callback: (Boolean, Double, String) -> Unit) {
        val col   = if (role == "driver") "driver" else "customer"
        val field = if (role == "driver") "Drvr_WalletBalance" else "Cust_WalletBalance"
        val ref   = firestore.collection(col).document(uid)
        firestore.runTransaction { tx ->
            val snap    = tx.get(ref)
            val current = snap.getDouble(field) ?: 0.0
            if (current < amount) throw Exception("Insufficient balance")
            val newBal = current - amount
            tx.update(ref, field, newBal)
            newBal
        }.addOnSuccessListener { newBal ->
            val txData = mapOf(
                "type"        to "payment",
                "amount"      to amount,   // stored as positive; sign shown by type
                "description" to description,
                "date"        to com.google.firebase.Timestamp.now()
            )
            firestore.collection(col).document(uid).collection("transactions").add(txData)
            handler.post { callback(true, newBal, "") }
        }.addOnFailureListener { e -> handler.post { callback(false, 0.0, e.message ?: "Deduction failed.") } }
    }

    fun walletCashOut(uid: String, role: String, amount: Double, callback: (Boolean, Double, String) -> Unit) {
        val col   = if (role == "driver") "driver" else "customer"
        val field = if (role == "driver") "Drvr_WalletBalance" else "Cust_WalletBalance"
        val ref   = firestore.collection(col).document(uid)
        firestore.runTransaction { tx ->
            val snap    = tx.get(ref)
            val current = snap.getDouble(field) ?: 0.0
            if (current < amount) throw Exception("Insufficient balance")
            val newBal = current - amount
            tx.update(ref, field, newBal)
            newBal
        }.addOnSuccessListener { newBal ->
            val txData = mapOf("type" to "cashout", "amount" to -amount, "date" to com.google.firebase.Timestamp.now())
            firestore.collection(col).document(uid).collection("transactions").add(txData)
            val ref2 = "CASHOUT-${System.currentTimeMillis()}"
            handler.post { callback(true, newBal, ref2) }
        }.addOnFailureListener { e -> handler.post { callback(false, 0.0, e.message ?: "Cashout failed.") } }
    }

    fun walletTransactions(uid: String, role: String, callback: (JSONArray?) -> Unit) {
        val col = if (role == "driver") "driver" else "customer"
        firestore.collection(col).document(uid).collection("transactions")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val arr = JSONArray()
                snap.documents.forEach { d ->
                    val raw  = d.data ?: return@forEach
                    val type = raw["type"]?.toString() ?: "payment"
                    // amount may be stored as negative for deductions — use absolute value
                    val amount = Math.abs((raw["amount"] as? Number)?.toDouble() ?: 0.0)
                    // Build a description if none stored
                    val desc = raw["description"]?.toString()
                        ?: raw["method"]?.let { "Top-up via ${it.toString().uppercase()}" }
                        ?: type.replaceFirstChar { it.uppercase() }
                    // Firestore Timestamp → ISO-like string for display
                    val dateStr = try {
                        val ts = raw["date"] as? com.google.firebase.Timestamp
                        if (ts != null) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            sdf.format(ts.toDate())
                        } else raw["date"]?.toString() ?: ""
                    } catch (_: Exception) { raw["date"]?.toString() ?: "" }

                    val obj = JSONObject().apply {
                        put("Tran_Type",        type)
                        put("Tran_Amount",       amount)
                        put("Tran_Description",  desc)
                        put("Tran_Date",         dateStr)
                        put("Tran_ReferenceNum", raw["reference"]?.toString() ?: "")
                    }
                    arr.put(obj)
                }
                handler.post { callback(arr) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    // ── Reports / Disputes ───────────────────────────────────────────────────

    fun reportDriver(
        reporterUid: String, driverUid: String,
        category: String, subject: String, details: String,
        callback: (Boolean, String) -> Unit
    ) {
        // Check for existing open report
        firestore.collection("dispute")
            .whereEqualTo("Disp_ReporterID", reporterUid)
            .whereEqualTo("Disp_Status", "open")
            .whereEqualTo("Disp_BookID", null)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    handler.post { callback(false, "You already have an open report pending review.") }
                    return@addOnSuccessListener
                }
                val data = mapOf(
                    "Disp_BookID"       to null,
                    "Disp_ReporterType" to "customer",
                    "Disp_ReporterID"   to reporterUid,
                    "Disp_AccusedType"  to "driver",
                    "Disp_AccusedID"    to driverUid,
                    "Disp_Subject"      to subject,
                    "Disp_Details"      to details,
                    "Disp_Category"     to category,
                    "Disp_Status"       to "open",
                    "Disp_CreatedAt"    to com.google.firebase.Timestamp.now()
                )
                firestore.collection("dispute").add(data)
                    .addOnSuccessListener { handler.post { callback(true, "") } }
                    .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Report failed.") } }
            }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Report failed.") } }
    }

    // ── Favourite Drivers ────────────────────────────────────────────────────

    fun setDriverFavourite(custUid: String, driverUid: String, status: String, callback: (Boolean, String) -> Unit) {
        val ref = firestore.collection("customer").document(custUid)
        val update = when (status) {
            "favourited" -> ref.update("Cust_FavouriteDrivers", FieldValue.arrayUnion(driverUid))
            "blocked"    -> ref.update("Cust_BlockedDrivers",   FieldValue.arrayUnion(driverUid))
            else         -> {
                // Remove from both arrays
                ref.update(mapOf(
                    "Cust_FavouriteDrivers" to FieldValue.arrayRemove(driverUid),
                    "Cust_BlockedDrivers"   to FieldValue.arrayRemove(driverUid)
                ))
                return
            }
        }
        update
            .addOnSuccessListener { handler.post { callback(true, "") } }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Failed.") } }
    }

    fun getMyDrivers(custUid: String, filter: String, callback: (JSONArray?) -> Unit) {
        val field = if (filter == "blocked") "Cust_BlockedDrivers" else "Cust_FavouriteDrivers"
        firestore.collection("customer").document(custUid).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ids = (doc.get(field) as? List<String>) ?: emptyList()
                if (ids.isEmpty()) { handler.post { callback(JSONArray()) }; return@addOnSuccessListener }
                // Fetch each driver document
                val arr = JSONArray()
                var done = 0
                ids.forEach { duid ->
                    firestore.collection("driver").document(duid).get()
                        .addOnSuccessListener { d ->
                            if (d.exists()) arr.put(JSONObject(d.data ?: emptyMap<String, Any>()))
                            done++
                            if (done == ids.size) handler.post { callback(arr) }
                        }
                        .addOnFailureListener {
                            done++
                            if (done == ids.size) handler.post { callback(arr) }
                        }
                }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    // ── Coupons ──────────────────────────────────────────────────────────────

    fun getActiveCoupons(callback: (JSONArray?) -> Unit) {
        firestore.collection("coupons")
            .whereEqualTo("Cpn_IsActive", true)
            .get()
            .addOnSuccessListener { snap ->
                val arr = JSONArray()
                snap.documents.forEach { d -> arr.put(JSONObject(d.data ?: emptyMap<String, Any>())) }
                handler.post { callback(arr) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    fun couponList(uid: String, callback: (JSONArray?) -> Unit) {
        firestore.collection("customer").document(uid).collection("coupons")
            .get()
            .addOnSuccessListener { snap ->
                val arr = JSONArray()
                snap.documents.forEach { d -> arr.put(JSONObject(d.data ?: emptyMap<String, Any>())) }
                handler.post { callback(arr) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    fun couponValidate(code: String, uid: String, fare: Double, callback: (JSONObject?) -> Unit) {
        firestore.collection("coupons")
            .whereEqualTo("Cpn_Code", code)
            .whereEqualTo("Cpn_IsActive", true)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) { handler.post { callback(null) }; return@addOnSuccessListener }
                val doc = snap.documents[0]
                handler.post { callback(JSONObject(doc.data ?: emptyMap<String, Any>())) }
            }
            .addOnFailureListener { handler.post { callback(null) } }
    }

    // ── Order ────────────────────────────────────────────────────────────────

    fun cancelOrder(orderId: String, callback: (Boolean, String) -> Unit) {
        firestore.collection("booking").document(orderId)
            .update("Book_Status", "cancelled")
            .addOnSuccessListener { handler.post { callback(true, "") } }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Cancel failed.") } }
    }

    // ── Proof of Delivery ────────────────────────────────────────────────────

    fun uploadProof(driverUid: String, orderId: String, photoBytes: ByteArray, callback: (Boolean, String) -> Unit) {
        val ref = storage.reference.child("proofs/$orderId/proof_${System.currentTimeMillis()}.jpg")
        ref.putBytes(photoBytes)
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { uri ->
                firestore.collection("booking").document(orderId)
                    .update("Book_ProofOfDelivery", uri.toString())
                handler.post { callback(true, uri.toString()) }
            }
            .addOnFailureListener { e -> handler.post { callback(false, e.message ?: "Upload failed.") } }
    }

    // ── External Services (no XAMPP needed) ──────────────────────────────────

    fun fetchOSRMRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double, callback: (List<org.osmdroid.util.GeoPoint>?) -> Unit) {
        Thread {
            try {
                val urlString = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "LalamoveAndroidSim")
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                        val points = ArrayList<org.osmdroid.util.GeoPoint>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            points.add(org.osmdroid.util.GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                        }
                        handler.post { callback(points) }
                        return@Thread
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            handler.post { callback(null) }
        }.start()
    }

    fun geocodeAddress(address: String, callback: (Double?, Double?) -> Unit) {
        Thread {
            try {
                val encoded = java.net.URLEncoder.encode(address, "UTF-8")
                val conn = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "LalamoveAndroid")
                val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val lat = obj.optDouble("lat", Double.NaN)
                    val lng = obj.optDouble("lon", Double.NaN)
                    if (!lat.isNaN() && !lng.isNaN()) { handler.post { callback(lat, lng) }; return@Thread }
                }
            } catch (e: Exception) { e.printStackTrace() }
            handler.post { callback(null, null) }
        }.start()
    }
}
