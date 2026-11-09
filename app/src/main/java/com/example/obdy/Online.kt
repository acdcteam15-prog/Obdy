package com.example.obdy

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Recall(val number: String, val component: String, val summary: String, val remedy: String)
class OnlineInfo(val recalls: List<Recall>, val complaintTotal: Int, val complaintTop: List<Pair<String, Int>>)

/** Public safety data from NHTSA (US government). It covers US-market cars; GCC cars are often built the same, but check with your dealer. */
object Online {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 25_000
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("NHTSA answered HTTP $code")
        return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    fun load(make: String, model: String, year: Int): OnlineInfo {
        val q = "make=${URLEncoder.encode(make, "UTF-8")}&model=${URLEncoder.encode(model, "UTF-8")}&modelYear=$year"
        val rec = JSONObject(get("https://api.nhtsa.gov/recalls/recallsByVehicle?$q")).optJSONArray("results")
        val recalls = ArrayList<Recall>()
        if (rec != null) for (i in 0 until rec.length()) {
            val o = rec.getJSONObject(i)
            recalls.add(Recall(o.optString("NHTSACampaignNumber"), o.optString("Component"), o.optString("Summary"), o.optString("Remedy")))
        }
        val comp = JSONObject(get("https://api.nhtsa.gov/complaints/complaintsByVehicle?$q")).optJSONArray("results")
        val counts = HashMap<String, Int>()
        var total = 0
        if (comp != null) for (i in 0 until comp.length()) {
            total++
            val comps = comp.getJSONObject(i).optString("components")
            for (c in comps.split(',')) { val k = c.trim(); if (k.isNotEmpty()) counts[k] = (counts[k] ?: 0) + 1 }
        }
        return OnlineInfo(recalls, total, counts.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value })
    }

    fun searchUrl(kind: String, query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (kind) {
            "youtube" -> "https://www.youtube.com/results?search_query=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }
}
