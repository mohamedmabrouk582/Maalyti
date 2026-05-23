package com.example.engine

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiSmsParser {
    private const val TAG = "GeminiSmsParser"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun parseWithGemini(smsBody: String): ParsedSMS? = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "BuildConfig.GEMINI_API_KEY not found: ${e.message}")
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("PLACEHOLDER")) {
            Log.e(TAG, "Gemini API Key is empty or placeholder. Fallback aborted.")
            return@withContext null
        }

        val prompt = """
            Extract transaction details from this bank SMS alert (can be in Arabic or English):
            "$smsBody"
            
            Return a raw JSON object with the following fields:
            - bankName: The name of the bank (e.g. Al Rajhi, CIB, SNB, Banque Misr, ENBD, ADIB etc.). No hardcoded bank names, infer from text or sender.
            - amount: The parsed decimal number. If not present or not a transaction, 0.0.
            - currency: The currency code (e.g. SAR, EGP, AED, USD etc.). Default to SAR if unknown but from KSA.
            - merchant: The business or merchant names, ATM ID, or payee. "Unspecified" if missing.
            - balance: The numeric available balance if mentioned, otherwise null.
            - cardLast4: The last 4 digits of the debit/credit card or account if mentioned, otherwise null.
            - type: Must be strictly one of: "withdraw", "deposit", "otp", "other".
            
            Format response as standard JSON. DO NOT wrap in ```json ``` markdown blocks.
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestObj = JSONObject()
        val contentsArray = org.json.JSONArray()
        val partsObj = JSONObject()
        partsObj.put("text", prompt)
        val partArray = org.json.JSONArray()
        partArray.put(partsObj)
        val contentObj = JSONObject()
        contentObj.put("parts", partArray)
        contentsArray.put(contentObj)
        requestObj.put("contents", contentsArray)

        // Request JSON Response Format
        val generationConfig = JSONObject()
        val responseFormatObj = JSONObject()
        responseFormatObj.put("mimeType", "application/json")
        generationConfig.put("responseFormat", responseFormatObj)
        requestObj.put("generationConfig", generationConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestObj.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code} / ${response.message}")
                    return@withContext null
                }
                val responseStr = response.body?.string() ?: return@withContext null
                Log.d(TAG, "Raw Response: $responseStr")

                // Parse standard Gemini JSON response
                val root = JSONObject(responseStr)
                val candidates = root.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        val text = parts.getJSONObject(0).getString("text")
                        Log.d(TAG, "Gemini parsed text: $text")

                        val pJson = JSONObject(text.trim())
                        val bankName = pJson.optString("bankName", "Unknown Bank")
                        val amount = pJson.optDouble("amount", 0.0)
                        val currency = pJson.optString("currency", "SAR")
                        val merchant = pJson.optString("merchant", "Unspecified")
                        val balance = if (pJson.isNull("balance")) null else pJson.optDouble("balance")
                        val cardLast4 = if (pJson.isNull("cardLast4")) null else pJson.optString("cardLast4")
                        val type = pJson.optString("type", "other")

                        // Format Date
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        val dateString = sdf.format(java.util.Date())

                        return@withContext ParsedSMS(
                            bankName = bankName,
                            amount = amount,
                            currency = currency,
                            merchant = merchant,
                            balance = balance,
                            cardLast4 = cardLast4,
                            dateString = dateString,
                            type = type,
                            confidence = 1.0, // Gemini fallback provides high assurance
                            rawBody = smsBody
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in Gemini parsing fallback", e)
        }
        return@withContext null
    }
}
