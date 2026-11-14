package com.example.obdy

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class AiType(val label: String) { Gemini("Google Gemini"), OpenAi("OpenAI-compatible"), Claude("Anthropic Claude") }

class AiProvider(val id: String, val type: AiType, val name: String, val key: String, val model: String, val baseUrl: String)
class AiPreset(val name: String, val type: AiType, val baseUrl: String, val model: String, val note: String)
class AiResult(val text: String?, val error: String?, val model: String?)

object AiPresets {
    val all = listOf(
        AiPreset("Google Gemini", AiType.Gemini, "", "gemini-3.5-flash", "Free keys at aistudio.google.com. Can also search Google."),
        AiPreset("Anthropic Claude", AiType.Claude, "", "claude-sonnet-5-5", "Key from console.anthropic.com (paid)."),
        AiPreset("OpenAI", AiType.OpenAi, "https://api.openai.com/v1", "gpt-4o-mini", "Key from platform.openai.com (paid). Change the model name to a current one."),
        AiPreset("OpenRouter", AiType.OpenAi, "https://openrouter.ai/api/v1", "openrouter/auto", "One key for many models, some free."),
        AiPreset("Groq", AiType.OpenAi, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "Fast, has a free tier."),
        AiPreset("Ollama on my laptop", AiType.OpenAi, "http://192.168.0.2:11434/v1", "llama3.1", "Free and private. Replace the address with your laptop's; the phone must reach it (same Wi-Fi or ZeroTier)."),
        AiPreset("Other (OpenAI-compatible)", AiType.OpenAi, "https://", "", "Any service that offers /chat/completions: LM Studio, DeepSeek, Mistral..."),
    )
}

/** One place that talks to every AI service. Nothing is sent until a button is pressed. */
object Ai {
    private val geminiFallbacks = listOf("gemini-3.5-flash", "gemini-3.7-flash", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite")

    fun ask(p: AiProvider, prompt: String, web: Boolean = false, image: ByteArray? = null): AiResult = try {
        when (p.type) {
            AiType.Gemini -> gemini(p, prompt, web, image)
            AiType.OpenAi -> openAi(p, prompt, image)
            AiType.Claude -> claude(p, prompt, image)
        }
    } catch (e: Exception) {
        AiResult(null, "Could not reach ${p.name} (${e.javaClass.simpleName}: ${e.message}). Check the internet, or the address for a local server.", null)
    }

    /** A tiny question to prove the key, the model name and the connection work. */
    fun test(p: AiProvider): AiResult = ask(p, "Reply with the single word OK.")

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20_000
        c.readTimeout = 240_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
        return code to text
    }

    private fun errorText(text: String): String = try {
        val o = JSONObject(text)
        val e = o.opt("error")
        if (e is JSONObject) e.optString("message") else if (e is String) e else text.take(200)
    } catch (ex: Exception) { text.take(200) }

    private fun explain(name: String, code: Int, msg: String) = when (code) {
        401, 403 -> "$name refused the key (HTTP $code). Check the key. $msg"
        404 -> "$name does not know that model or address (HTTP 404). Check the model name and the base address. $msg"
        429 -> "$name says too many requests or the quota is used up (HTTP 429). Wait and try again. $msg"
        else -> "$name error $code: $msg"
    }

    // ---------------------------------------------------------------- Gemini
    private fun b64(b: ByteArray): String = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

    private fun gemini(p: AiProvider, prompt: String, web: Boolean, image: ByteArray?): AiResult {
        if (p.key.isBlank()) return AiResult(null, "Add the API key for ${p.name} first.", null)
        val models = (listOf(p.model) + geminiFallbacks).filter { it.isNotBlank() }.distinct()
        var last = "No model answered."
        for (m in models) {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", geminiParts(prompt, image))))
                .put("generationConfig", JSONObject().put("temperature", 0.3))
            if (web) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            val (code, text) = post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent", mapOf("x-goog-api-key" to p.key), body)
            if (code in 200..299) {
                val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                if (sb.isNotBlank()) return AiResult(sb.toString().trim(), null, m)
                last = "$m returned an empty answer."; continue
            }
            val msg = errorText(text)
            last = explain(p.name, code, msg)
            if (code != 404 && !(code == 400 && msg.contains("model", ignoreCase = true))) return AiResult(null, last, null)
        }
        return AiResult(null, last, null)
    }

    private fun geminiParts(prompt: String, image: ByteArray?): JSONArray {
        val parts = JSONArray()
        if (image != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64(image))))
        parts.put(JSONObject().put("text", prompt))
        return parts
    }

    // ---------------------------------------------------------------- OpenAI-compatible
    private fun openAi(p: AiProvider, prompt: String, image: ByteArray?): AiResult {
        val base = p.baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http")) return AiResult(null, "Add the base address for ${p.name} (for example https://api.openai.com/v1).", null)
        if (p.model.isBlank()) return AiResult(null, "Add a model name for ${p.name}.", null)
        val headers = HashMap<String, String>()
        if (p.key.isNotBlank()) headers["Authorization"] = "Bearer ${p.key}"
        val content: Any = if (image == null) prompt else JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + b64(image))))
        val body = JSONObject().put("model", p.model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val (code, text) = post("$base/chat/completions", headers, body)
        if (code !in 200..299) return AiResult(null, explain(p.name, code, errorText(text)), null)
        val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val reply = msg.opt("content")
        val out = if (reply is JSONArray) (0 until reply.length()).joinToString("") { reply.getJSONObject(it).optString("text") } else msg.optString("content")
        return if (out.isBlank()) AiResult(null, "${p.model} returned an empty answer.", null) else AiResult(out.trim(), null, p.model)
    }

    // ---------------------------------------------------------------- Anthropic Claude
    private fun claude(p: AiProvider, prompt: String, image: ByteArray?): AiResult {
        if (p.key.isBlank()) return AiResult(null, "Add the API key for ${p.name} first.", null)
        if (p.model.isBlank()) return AiResult(null, "Add a model name for ${p.name}.", null)
        val content: Any = if (image == null) prompt else JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(image))))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject().put("model", p.model).put("max_tokens", 8000)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val (code, text) = post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to p.key, "anthropic-version" to "2023-06-01"), body)
        if (code !in 200..299) return AiResult(null, explain(p.name, code, errorText(text)), null)
        val arr = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) { val b = arr.getJSONObject(i); if (b.optString("type") == "text") sb.append(b.optString("text")) }
        return if (sb.isBlank()) AiResult(null, "${p.model} returned an empty answer.", null) else AiResult(sb.toString().trim(), null, p.model)
    }

    /** Pulls the first {...} block out of an answer (models sometimes wrap JSON in text or code fences). */
    fun extractJson(text: String): JSONObject? {
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(text.substring(a, b + 1)) } catch (e: Exception) { null }
    }
}
