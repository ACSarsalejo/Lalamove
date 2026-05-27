package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SearchResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

class LocationSelectionActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var clearButton: ImageView
    private lateinit var listView: ListView
    private lateinit var resultsTitle: TextView
    private lateinit var emptyText: TextView

    private var locationType: String = "pickup"
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val results = mutableListOf<SearchResult>()
    private lateinit var resultsAdapter: SearchResultAdapter

    // Cached Overpass results — populated once on screen open
    private val landmarkSuggestions = mutableListOf<SearchResult>()

    private val addressDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, result.data)
            finish()
        }
    }

    private inner class SearchResultAdapter(
        private val items: MutableList<SearchResult>
    ) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(this@LocationSelectionActivity)
                .inflate(R.layout.item_search_result, parent, false)
            val item = items[pos]
            row.findViewById<TextView>(R.id.resultName).text = item.name
            row.findViewById<TextView>(R.id.resultAddress).text = item.address
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_selection)

        locationType = intent.getStringExtra("LOCATION_TYPE") ?: "pickup"

        searchInput  = findViewById(R.id.searchInput)
        clearButton  = findViewById(R.id.clearButton)
        listView     = findViewById(R.id.listViewLandmarks)
        resultsTitle = findViewById(R.id.resultsTitle)
        emptyText    = findViewById(R.id.emptyText)

        resultsAdapter = SearchResultAdapter(results)
        listView.adapter = resultsAdapter

        listView.setOnItemClickListener { _, _, position, _ ->
            openAddressDetails(results[position])
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        clearButton.setOnClickListener {
            searchInput.setText("")
            clearButton.visibility = View.GONE
            showDefaultSuggestions()
        }

        setupSearch()
        fetchOverpassLandmarks()
    }

    // ── Overpass API: notable places in Cebu bounding box ─────────────────────

    private fun fetchOverpassLandmarks() {
        resultsTitle.text = "Loading suggestions…"
        emptyText.visibility = View.GONE

        // Cebu bbox: south=9.9 west=123.3 north=10.8 east=124.1
        // Queries malls, universities, colleges, hospitals, airports, markets, attractions
        val query = """
            [out:json][timeout:15];
            (
              node["shop"="mall"]["name"](9.9,123.3,10.8,124.1);
              way["shop"="mall"]["name"](9.9,123.3,10.8,124.1);
              node["amenity"="university"]["name"](9.9,123.3,10.8,124.1);
              way["amenity"="university"]["name"](9.9,123.3,10.8,124.1);
              node["amenity"="college"]["name"](9.9,123.3,10.8,124.1);
              node["amenity"="hospital"]["name"](9.9,123.3,10.8,124.1);
              way["amenity"="hospital"]["name"](9.9,123.3,10.8,124.1);
              node["aeroway"="aerodrome"]["name"](9.9,123.3,10.8,124.1);
              node["amenity"="marketplace"]["name"](9.9,123.3,10.8,124.1);
              node["tourism"="attraction"]["name"](9.9,123.3,10.8,124.1);
              node["office"="government"]["name"](9.9,123.3,10.8,124.1);
            );
            out center 40;
        """.trimIndent()

        Thread {
            try {
                val conn = URL("https://overpass-api.de/api/interpreter")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
                conn.outputStream.use { it.write(body.toByteArray()) }
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val elements = JSONObject(text).optJSONArray("elements") ?: JSONArray()
                val found = mutableListOf<SearchResult>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags") ?: continue
                    val name = tags.optString("name", "").takeIf { it.isNotBlank() } ?: continue

                    // Coords: nodes have lat/lon directly; ways have a center object
                    val lat = when {
                        el.has("lat") -> el.getDouble("lat")
                        el.has("center") -> el.getJSONObject("center").getDouble("lat")
                        else -> continue
                    }
                    val lng = when {
                        el.has("lon") -> el.getDouble("lon")
                        el.has("center") -> el.getJSONObject("center").getDouble("lon")
                        else -> continue
                    }

                    val address = buildAddress(tags)
                    found.add(SearchResult(name, address, lat, lng))
                }

                // Deduplicate by name (ways and nodes can appear for the same place)
                val deduped = found.distinctBy { it.name }.sortedBy { it.name }

                runOnUiThread {
                    landmarkSuggestions.clear()
                    landmarkSuggestions.addAll(deduped)
                    if (searchInput.text.isNullOrBlank()) showDefaultSuggestions()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (searchInput.text.isNullOrBlank()) {
                        resultsTitle.text = "Type to search for a location"
                    }
                }
            }
        }.start()
    }

    /** Builds a short human-readable address from OSM tags. */
    private fun buildAddress(tags: JSONObject): String {
        val parts = listOfNotNull(
            tags.optString("addr:street", "").takeIf { it.isNotBlank() },
            tags.optString("addr:suburb", "").takeIf { it.isNotBlank() }
                ?: tags.optString("addr:barangay", "").takeIf { it.isNotBlank() },
            tags.optString("addr:city", "").takeIf { it.isNotBlank() }
                ?: tags.optString("addr:municipality", "").takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Cebu, Philippines"
    }

    private fun showDefaultSuggestions() {
        results.clear()
        results.addAll(landmarkSuggestions)
        resultsAdapter.notifyDataSetChanged()
        resultsTitle.text = if (landmarkSuggestions.isNotEmpty())
            "Popular places in Cebu"
        else
            "Type to search for a location"
        emptyText.visibility = View.GONE
    }

    // ── Nominatim text search ──────────────────────────────────────────────────

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val query = s?.toString()?.trim() ?: ""
                clearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                if (query.length < 2) {
                    showDefaultSuggestions()
                    return
                }
                searchRunnable = Runnable { searchNominatim(query) }
                searchHandler.postDelayed(searchRunnable!!, 350)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchNominatim(query: String) {
        resultsTitle.text = "Searching…"
        emptyText.visibility = View.GONE

        Thread {
            try {
                val encoded = java.net.URLEncoder.encode("$query cebu", "UTF-8")
                val urlStr = "https://nominatim.openstreetmap.org/search" +
                        "?q=$encoded" +
                        "&format=json" +
                        "&countrycodes=ph" +
                        "&viewbox=123.3,10.8,124.1,9.9" +
                        "&bounded=1" +
                        "&limit=12" +
                        "&addressdetails=1"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", packageName)
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val arr = JSONArray(text)
                val found = mutableListOf<SearchResult>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val displayName = obj.optString("display_name", "")
                    val parts = displayName.split(",")
                    val name    = parts.take(2).joinToString(",").trim()
                    val address = parts.drop(2).take(3).joinToString(",").trim()
                    val lat = obj.optDouble("lat", 0.0)
                    val lng = obj.optDouble("lon", 0.0)
                    if (lat != 0.0 && lng != 0.0) found.add(SearchResult(name, address, lat, lng))
                }

                runOnUiThread {
                    results.clear()
                    results.addAll(found)
                    resultsAdapter.notifyDataSetChanged()
                    if (found.isEmpty()) {
                        resultsTitle.text = "No results found"
                        emptyText.visibility = View.VISIBLE
                    } else {
                        resultsTitle.text = "${found.size} result(s)"
                        emptyText.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultsTitle.text = "Search failed — check your connection"
                }
            }
        }.start()
    }

    private fun openAddressDetails(result: SearchResult) {
        addressDetailsLauncher.launch(
            Intent(this, AddressDetailsActivity::class.java).apply {
                putExtra("LOCATION_NAME",    result.name)
                putExtra("LOCATION_ADDRESS", result.address)
                putExtra("LOCATION_LAT",     result.lat)
                putExtra("LOCATION_LNG",     result.lng)
                putExtra("LOCATION_TYPE",    locationType)
            }
        )
    }
}
