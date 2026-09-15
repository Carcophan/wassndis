package com.example.wassndis.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.wassndis.data.QaItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class GeminiAnalysisResult(
    val title: String,
    val shortDescription: String,
    val fullDescription: String,
    val tags: List<String>,
    val mainObject: String = "",
    val category: String = "",
    val objectDetails: Map<String, String> = emptyMap()
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeImage(
        imageFile: File,
        apiKey: String,
        modelName: String = "gemini-3.8-flash"
    ): Result<GeminiAnalysisResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Bitte hinterlege zuerst deinen Gemini API-Key in den Einstellungen.")
            )
        }

        try {
            val base64Image = prepareImageBase64(imageFile)
            val effectiveModel = if (modelName.isBlank()) "gemini-3.8-flash" else modelName.trim()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

            val prompt = """
                Du bist ein hochpräziser Experte für visuelle Objekterkennung, Sachverständigen-Gutachten und Detailanalyse für die App "wassndis" ("Was ist das?").

                DEINE OBERSTE REGEL:
                Beschreibe NICHT das Foto oder die Szene allgemein! Vermeide Floskeln wie "Auf dem Bild sieht man...", "Das Foto zeigt...", "Im Vordergrund/Hintergrund steht..." oder Beschreibungen von Bildhintergrund, Beleuchtung oder Fotoperspektive.
                Konzentriere dich VOLLSTÄNDIG und AUSSCHLIESSLICH auf den konkreten HAUPTGEGENSTAND IM FOKUS.

                Analysiere den Gegenstand wie ein Fachlexikon oder Sachverständiger:
                1. Bestimme die exakte Bezeichnung: Wenn erkennbar Hersteller, Modell, Typnummer, Bauform oder genaue biologische/technische Art (z. B. 'De'Longhi Dedica EC 685 Siebträgermaschine', 'Monstera Deliciosa', 'Bosch Professional GSR 18V-55').
                2. Erkläre Funktion, Einsatzzweck und Funktionsweise des Gegenstands.
                3. Analysiere sichtbare Komponenten, Bedienelemente, Schalter, Anschlüsse und mechanische Teile.
                4. Erfasse Material, Verarbeitung, Oberflächen und sichtbare Kennzeichnungen/Beschriftungen.
                5. Schätze den Erhaltungszustand und Besonderheiten ein.

                Gib deine Antwort zwingend als valides JSON-Objekt mit exakt dieser Struktur zurück:
                {
                  "mainObject": "Präziser Name des Hauptgegenstands (z. B. 'De'Longhi Dedica EC 685 Espressomaschine')",
                  "category": "Kategorie / Fachgebiet (z. B. 'Haushaltsgerät / Kaffeemaschine', 'Handwerkzeug', 'Zimmerpflanze', 'Unterhaltungselektronik')",
                  "title": "Kompakter Name des Gegenstands für die Titelleiste",
                  "shortDescription": "1 bis 2 prägnante Sätze über Wesen, Kernfunktion und Nutzen dieses Gegenstands (keine Fotobeschreibung!)",
                  "details": {
                    "Genaue Bezeichnung": "Hersteller, Modellname oder Spezifikation",
                    "Funktion & Einsatzzweck": "Wofür dieser Gegenstand dient und wie er angewendet wird",
                    "Aufbau & Komponenten": "Sichtbare Teile, Bedienelemente, Anschlüsse oder Mechanik",
                    "Material & Verarbeitung": "Verwendete Werkstoffe (z. B. gebürsteter Edelstahl, ABS-Kunststoff, Gusseisen)",
                    "Farbe & Design": "Farbgebung, Formensprache und Oberflächenfinish",
                    "Zustand": "Sichtbarer Erhaltungsgrad (z. B. neuwertig, gepflegt, mit typischen Gebrauchsspuren)",
                    "Besondere Merkmale": "Auffällige Konstruktionsmerkmale oder Spezialfunktionen",
                    "Kennzeichnungen": "Sichtbare Logos, Typenbezeichnungen oder Skalen"
                  },
                  "fullDescription": "Fundierte, tiefgehende Gegenstandsanalyse wie in einem Fachlexikon: Detaillierte Erläuterung der Funktionsweise, der einzelnen Komponenten, des Einsatzbereichs, relevanter technischer Eigenschaften sowie wissenswerter Hintergründe zu diesem Objekt.",
                  "tags": ["Schlagwort1", "Schlagwort2", "Schlagwort3", "Schlagwort4"]
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Text prompt
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Image data
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.2)
                })
            }

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    val errorMessage = parseErrorMessage(response.code, responseBody, effectiveModel)
                    return@withContext Result.failure(Exception(errorMessage))
                }

                val analysisResult = parseResponse(responseBody)
                Result.success(analysisResult)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun askQuestionAboutImage(
        imageFile: File,
        question: String,
        previousQuestions: List<QaItem> = emptyList(),
        mainObject: String = "",
        apiKey: String,
        modelName: String = "gemini-3.8-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Bitte hinterlege zuerst deinen Gemini API-Key in den Einstellungen.")
            )
        }

        try {
            val base64Image = prepareImageBase64(imageFile)
            val effectiveModel = if (modelName.isBlank()) "gemini-3.8-flash" else modelName.trim()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

            val promptBuilder = StringBuilder()
            promptBuilder.appendLine("Du bist ein hochpräziser Experte und Sachverständiger für die App 'wassndis' ('Was ist das?').")
            if (mainObject.isNotBlank()) {
                promptBuilder.appendLine("Der Gegenstand auf dem beigefügten Foto wurde als '$mainObject' identifiziert.")
            }
            promptBuilder.appendLine("Der Benutzer hat eine konkrete Nachfrage zu diesem Gegenstand bzw. Foto.")
            promptBuilder.appendLine("Beantworte die Frage sachkundig, präzise, direkt und verständlich auf Deutsch.")
            promptBuilder.appendLine("Nutze bei Bedarf strukturierte Absätze oder Aufzählungspunkte, vermeide unnötige Floskeln.")

            if (previousQuestions.isNotEmpty()) {
                promptBuilder.appendLine("\nBisheriger Fragen-Verlauf zu diesem Gegenstand:")
                previousQuestions.takeLast(5).forEach { qa ->
                    promptBuilder.appendLine("Frage: ${qa.question}")
                    promptBuilder.appendLine("Antwort: ${qa.answer}")
                }
            }

            promptBuilder.appendLine("\nAktuelle Frage des Benutzers:")
            promptBuilder.appendLine(question.trim())

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptBuilder.toString())
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                })
            }

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMessage = parseErrorMessage(response.code, responseBody, effectiveModel)
                    return@withContext Result.failure(Exception(errorMessage))
                }

                val root = JSONObject(responseBody)
                val candidates = root.optJSONArray("candidates")
                    ?: throw IllegalStateException("Keine Antwort von Gemini erhalten.")
                if (candidates.length() == 0) {
                    throw IllegalStateException("Leere Antwort von Gemini.")
                }
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val answerText = parts.getJSONObject(0).getString("text").trim()
                Result.success(answerText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun parseResponse(responseBody: String): GeminiAnalysisResult {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Keine Antwort-Kandidaten von Gemini erhalten.")
        
        if (candidates.length() == 0) {
            throw IllegalStateException("Leere Antwort von Gemini.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val rawText = parts.getJSONObject(0).getString("text")

        // Parse JSON from text (remove markdown backticks if any)
        val cleanText = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = try {
            JSONObject(cleanText)
        } catch (e: Exception) {
            // Fallback if model didn't return pure json
            return GeminiAnalysisResult(
                title = "Foto-Analyse",
                shortDescription = cleanText.take(160) + if (cleanText.length > 160) "..." else "",
                fullDescription = cleanText,
                tags = listOf("Analyse", "Gemini"),
                mainObject = "Hauptgegenstand",
                category = "",
                objectDetails = emptyMap()
            )
        }

        val mainObject = json.optString("mainObject", "").ifBlank {
            json.optString("title", "Hauptgegenstand")
        }
        val category = json.optString("category", "")
        val title = json.optString("title", mainObject).ifBlank { mainObject }
        val shortDesc = json.optString("shortDescription", "")
        val fullDesc = json.optString("fullDescription", "")

        val detailsMap = linkedMapOf<String, String>()
        val detailsJson = json.optJSONObject("details")
        if (detailsJson != null) {
            val keys = detailsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = detailsJson.optString(key, "")
                if (value.isNotBlank()) {
                    detailsMap[key] = value
                }
            }
        }
        
        val tagsList = mutableListOf<String>()
        val tagsArr = json.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val tag = tagsArr.optString(i)
                if (tag.isNotBlank()) tagsList.add(tag)
            }
        }

        return GeminiAnalysisResult(
            title = title,
            shortDescription = shortDesc,
            fullDescription = fullDesc,
            tags = tagsList,
            mainObject = mainObject,
            category = category,
            objectDetails = detailsMap
        )
    }

    private fun parseErrorMessage(code: Int, body: String, model: String): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: body
            when (code) {
                400 -> "Ungültige Anfrage ($code): $message"
                403 -> "Zugriff verweigert ($code): Bitte prüfe deinen Gemini API-Key."
                404 -> "Modell '$model' nicht gefunden ($code). Eventuell in den Einstellungen auf 'gemini-2.5-flash' umstellen."
                429 -> "Ratenlimit / Quota überschritten ($code). Bitte kurz warten."
                else -> "Gemini API Fehler ($code): $message"
            }
        } catch (e: Exception) {
            "HTTP $code: $body"
        }
    }

    private fun prepareImageBase64(imageFile: File): String {
        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw IllegalStateException("Konnte Bilddatei nicht lesen: ${imageFile.name}")

        // Scale bitmap down to max 1568px dimension to save bandwidth and stay well within Gemini limits
        val maxDim = 1568
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val (targetW, targetH) = if (ratio > 1f) {
                Pair(maxDim, (maxDim / ratio).toInt())
            } else {
                Pair((maxDim * ratio).toInt(), maxDim)
            }
            Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
        } else {
            originalBitmap
        }

        val outStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
        val byteArray = outStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
