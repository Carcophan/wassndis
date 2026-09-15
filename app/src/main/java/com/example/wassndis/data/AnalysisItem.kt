package com.example.wassndis.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QaItem(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AnalysisItem(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val title: String,
    val shortDescription: String,
    val fullDescription: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val modelUsed: String = "gemini-3.8-flash",
    val mainObject: String = "",
    val category: String = "",
    val objectDetails: Map<String, String> = emptyMap(),
    val questions: List<QaItem> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("imagePath", imagePath)
            put("title", title)
            put("shortDescription", shortDescription)
            put("fullDescription", fullDescription)
            put("timestamp", timestamp)
            put("tags", JSONArray(tags))
            put("modelUsed", modelUsed)
            put("mainObject", mainObject)
            put("category", category)
            val detailsJson = JSONObject()
            objectDetails.forEach { (k, v) -> detailsJson.put(k, v) }
            put("objectDetails", detailsJson)
            val questionsArray = JSONArray()
            questions.forEach { q ->
                questionsArray.put(JSONObject().apply {
                    put("id", q.id)
                    put("question", q.question)
                    put("answer", q.answer)
                    put("timestamp", q.timestamp)
                })
            }
            put("questions", questionsArray)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AnalysisItem {
            val tagsList = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.optString(i))
                }
            }

            val detailsMap = mutableMapOf<String, String>()
            val detailsObj = json.optJSONObject("objectDetails")
            if (detailsObj != null) {
                val keys = detailsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    detailsMap[key] = detailsObj.optString(key, "")
                }
            }

            val questionsList = mutableListOf<QaItem>()
            val questionsArray = json.optJSONArray("questions")
            if (questionsArray != null) {
                for (i in 0 until questionsArray.length()) {
                    val qObj = questionsArray.optJSONObject(i)
                    if (qObj != null) {
                        questionsList.add(
                            QaItem(
                                id = qObj.optString("id", UUID.randomUUID().toString()),
                                question = qObj.optString("question", ""),
                                answer = qObj.optString("answer", ""),
                                timestamp = qObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val parsedTitle = json.optString("title", "Foto-Analyse")
            val parsedMainObject = json.optString("mainObject", parsedTitle)

            return AnalysisItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                imagePath = json.optString("imagePath", ""),
                title = parsedTitle,
                shortDescription = json.optString("shortDescription", ""),
                fullDescription = json.optString("fullDescription", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                tags = tagsList,
                modelUsed = json.optString("modelUsed", "gemini-3.8-flash"),
                mainObject = parsedMainObject,
                category = json.optString("category", ""),
                objectDetails = detailsMap,
                questions = questionsList
            )
        }
    }
}
