package com.example.lalamove

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(
    val role: String,
    val acctId: Int,
    val userId: Long,
    val name: String,
    val email: String,
    val phone: String,
    val isVerified: Boolean
)

object ApiClient {

    // Emulator uses 10.0.2.2 to reach the host machine's localhost.
    // Change to your computer's LAN IP (e.g. "http://192.168.1.10/lalamove/api/")
    // when testing on a physical device connected to the same Wi-Fi.
    const val BASE_URL = "http://10.0.2.2/lalamove/api/"

    fun triggerSync() {
        Thread {
            try {
                val syncUrl = BASE_URL.replace("/api/", "/includes/") + "run_sync.php"
                val conn = URL(syncUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.inputStream.readBytes()
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun fetchOSRMRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double, callback: (List<org.osmdroid.util.GeoPoint>?) -> Unit) {
        Thread {
            try {
                val urlString = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "LalamoveAndroidSim")
                
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val geometry = route.getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")
                        val points = ArrayList<org.osmdroid.util.GeoPoint>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lng = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            points.add(org.osmdroid.util.GeoPoint(lat, lng))
                        }
                        mainHandler.post { callback(points) }
                        return@Thread
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mainHandler.post { callback(null) }
        }.start()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun login(identifier: String, password: String, callback: (LoginResult?, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("identifier", identifier)
                    put("password", password)
                }
                val response = post("login.php", body)
                if (response.optBoolean("success")) {
                    val result = LoginResult(
                        role        = response.optString("role", "customer"),
                        acctId      = response.optInt("acct_id", 0),
                        userId      = response.optLong("user_id", 0L),
                        name        = response.optString("name", ""),
                        email       = response.optString("email", ""),
                        phone       = response.optString("phone", ""),
                        isVerified  = response.optBoolean("is_verified", false)
                    )
                    mainHandler.post { callback(result, "") }
                } else {
                    val error = response.optString("error", "Login failed")
                    mainHandler.post { callback(null, error) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(null, "Cannot reach server. Is XAMPP running?") }
            }
        }.start()
    }

    // callback: (success, userId, acctId, error)
    fun register(
        firstName: String, lastName: String,
        phone: String, email: String, password: String,
        callback: (Boolean, Long, Int, String) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("first_name", firstName)
                    put("last_name",  lastName)
                    put("phone",      phone)
                    put("email",      email)
                    put("password",   password)
                }
                val response = post("register.php", body)
                if (response.optBoolean("success")) {
                    val userId = response.optLong("user_id", 0L)
                    val acctId = response.optInt("acct_id", 0)
                    mainHandler.post { callback(true, userId, acctId, "") }
                } else {
                    val error = response.optString("error", "Registration failed")
                    mainHandler.post { callback(false, 0L, 0, error) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, 0L, 0, "Cannot reach server. Is XAMPP running?") }
            }
        }.start()
    }

    // callback: (success, userId, acctId, error)
    fun registerDriver(
        firstName: String, lastName: String,
        phone: String, email: String, password: String, vehicleType: String,
        callback: (Boolean, Long, Int, String) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("first_name",   firstName)
                    put("last_name",    lastName)
                    put("phone",        phone)
                    put("email",        email)
                    put("password",     password)
                    put("vehicle_type", vehicleType)
                }
                val response = post("register_driver.php", body)
                if (response.optBoolean("success")) {
                    val userId = response.optLong("user_id", 0L)
                    val acctId = response.optInt("acct_id", 0)
                    mainHandler.post { callback(true, userId, acctId, "") }
                } else {
                    val error = response.optString("error", "Registration failed")
                    mainHandler.post { callback(false, 0L, 0, error) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, 0L, 0, "Cannot reach server. Is XAMPP running?") }
            }
        }.start()
    }

    fun getProfile(acctId: Int, callback: (JSONObject?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}get_profile.php?acct_id=$acctId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                mainHandler.post { callback(if (json.optBoolean("success")) json else null) }
            } catch (e: Exception) {
                mainHandler.post { callback(null) }
            }
        }.start()
    }

    fun updateProfile(
        acctId: Int, firstName: String, lastName: String, phone: String, email: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("first_name", firstName)
                    put("last_name", lastName); put("phone", phone); put("email", email)
                }
                val r = post("update_profile.php", body)
                if (r.optBoolean("success")) mainHandler.post { callback(true, "") }
                else mainHandler.post { callback(false, r.optString("error", "Update failed")) }
            } catch (e: Exception) { mainHandler.post { callback(false, "Cannot reach server.") } }
        }.start()
    }

    fun changePassword(
        acctId: Int, currentPw: String, newPw: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("current_password", currentPw); put("new_password", newPw)
                }
                val r = post("change_password.php", body)
                if (r.optBoolean("success")) mainHandler.post { callback(true, "") }
                else mainHandler.post { callback(false, r.optString("error", "Failed")) }
            } catch (e: Exception) { mainHandler.post { callback(false, "Cannot reach server.") } }
        }.start()
    }

    fun updatePhoto(acctId: Int, photoBytes: ByteArray, mimeType: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val boundary = "----FormBoundary" + System.currentTimeMillis()
                val url = URL(BASE_URL + "update_photo.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 15000

                DataOutputStream(conn.outputStream).use { out ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"acct_id\"\r\n\r\n")
                    out.writeBytes("$acctId\r\n")
                    val ext = if (mimeType.contains("png")) "png" else "jpg"
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.$ext\"\r\n")
                    out.writeBytes("Content-Type: $mimeType\r\n\r\n")
                    out.write(photoBytes)
                    out.writeBytes("\r\n--$boundary--\r\n")
                }

                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val r = JSONObject(text)
                if (r.optBoolean("success")) mainHandler.post { callback(true, r.optString("photo_url", "")) }
                else mainHandler.post { callback(false, r.optString("error", "Upload failed")) }
            } catch (e: Exception) { mainHandler.post { callback(false, "Cannot reach server.") } }
        }.start()
    }

    fun getAddresses(acctId: Int, callback: (JSONArray?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}addresses.php?acct_id=$acctId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val arr = if (json.optBoolean("success")) json.optJSONArray("addresses") else null
                mainHandler.post { callback(arr) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun addAddress(acctId: Int, label: String, address: String, type: String, callback: (Boolean, Int) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("action", "add")
                    put("label", label); put("address", address); put("type", type)
                }
                val r = post("addresses.php", body)
                if (r.optBoolean("success")) mainHandler.post { callback(true, r.optInt("addr_id")) }
                else mainHandler.post { callback(false, 0) }
            } catch (e: Exception) { mainHandler.post { callback(false, 0) } }
        }.start()
    }

    fun updateAddress(acctId: Int, addrId: Int, label: String, address: String, type: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("action", "update"); put("addr_id", addrId)
                    put("label", label); put("address", address); put("type", type)
                }
                val r = post("addresses.php", body)
                mainHandler.post { callback(r.optBoolean("success")) }
            } catch (e: Exception) { mainHandler.post { callback(false) } }
        }.start()
    }

    fun deleteAddress(acctId: Int, addrId: Int, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("action", "delete"); put("addr_id", addrId)
                }
                val r = post("addresses.php", body)
                mainHandler.post { callback(r.optBoolean("success")) }
            } catch (e: Exception) { mainHandler.post { callback(false) } }
        }.start()
    }

    fun reportDriver(
        acctId: Int, driverId: Int, category: String, subject: String, details: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("driver_id", driverId)
                    put("category", category); put("subject", subject); put("details", details)
                }
                val r = post("report_driver.php", body)
                if (r.optBoolean("success")) mainHandler.post { callback(true, "") }
                else mainHandler.post { callback(false, r.optString("error", "Report failed")) }
            } catch (e: Exception) { mainHandler.post { callback(false, "Cannot reach server.") } }
        }.start()
    }

    fun setDriverFavourite(acctId: Int, driverId: Int, status: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("acct_id", acctId); put("driver_id", driverId); put("status", status)
                }
                val r = post("favourite_driver.php", body)
                if (r.optBoolean("success")) mainHandler.post { callback(true, "") }
                else mainHandler.post { callback(false, r.optString("error", "Failed")) }
            } catch (e: Exception) { mainHandler.post { callback(false, "Cannot reach server.") } }
        }.start()
    }

    fun getMyDrivers(acctId: Int, filter: String, callback: (JSONArray?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}favourite_driver.php?acct_id=$acctId&filter=$filter")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val arr = if (json.optBoolean("success")) json.optJSONArray("drivers") else null
                mainHandler.post { callback(arr) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun getActiveCoupons(callback: (JSONArray?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}active_coupons.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val arr = if (json.optBoolean("success")) json.optJSONArray("coupons") else null
                mainHandler.post { callback(arr) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun walletBalance(userId: Long, role: String, callback: (Double?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}wallet_balance.php?user_id=$userId&role=$role")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val balance = if (json.optBoolean("success")) json.optDouble("balance") else null
                mainHandler.post { callback(balance) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun walletTopUp(userId: Long, role: String, amount: Double, method: String = "gcash", callback: (Boolean, Double, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("user_id", userId); put("role", role); put("amount", amount); put("method", method)
                }
                val r = post("wallet_topup.php", body)
                if (r.optBoolean("success")) {
                    mainHandler.post { callback(true, r.optDouble("balance"), r.optString("ref")) }
                } else {
                    mainHandler.post { callback(false, 0.0, r.optString("error", "Top-up failed")) }
                }
            } catch (e: Exception) { mainHandler.post { callback(false, 0.0, "Cannot reach server.") } }
        }.start()
    }

    fun walletDeduct(userId: Long, role: String, amount: Double, description: String, callback: (Boolean, Double, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("user_id", userId); put("role", role)
                    put("amount", amount); put("description", description)
                }
                val r = post("wallet_deduct.php", body)
                if (r.optBoolean("success")) {
                    mainHandler.post { callback(true, r.optDouble("balance"), "") }
                } else {
                    mainHandler.post { callback(false, 0.0, r.optString("error", "Deduction failed")) }
                }
            } catch (e: Exception) { mainHandler.post { callback(false, 0.0, "Cannot reach server.") } }
        }.start()
    }

    fun couponValidate(code: String, userId: Long, fare: Double, callback: (JSONObject?) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("code", code); put("user_id", userId); put("fare", fare)
                }
                val r = post("coupon_validate.php", body)
                mainHandler.post { callback(r) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun walletTransactions(userId: Long, role: String, callback: (JSONArray?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}wallet_transactions.php?user_id=$userId&role=$role")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val arr = if (json.optBoolean("success")) json.optJSONArray("transactions") else null
                mainHandler.post { callback(arr) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    fun couponList(userId: Long, callback: (JSONArray?) -> Unit) {
        Thread {
            try {
                val url = URL("${BASE_URL}coupon_list.php?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val arr = if (json.optBoolean("success")) json.optJSONArray("coupons") else null
                mainHandler.post { callback(arr) }
            } catch (e: Exception) { mainHandler.post { callback(null) } }
        }.start()
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val url = URL(BASE_URL + endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val responseText = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(responseText)
    }
}
