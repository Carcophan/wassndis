package com.example.wassndis

import com.example.wassndis.data.AnalysisItem
import org.junit.Assert.*
import org.junit.Test

class AnalysisItemTest {

    @Test
    fun testSerializationAndDeserialization() {
        val item = AnalysisItem(
            id = "test-uuid-1234",
            imagePath = "/storage/emulated/0/wassndis/test.jpg",
            title = "Kuckucksuhr aus dem Schwarzwald",
            shortDescription = "Eine traditionelle handgeschnitzte Holzuhr.",
            fullDescription = "Die Uhr zeigt detaillierte Schnitzereien mit Blättern und einem Kuckucksvogel.",
            timestamp = 1715000000000L,
            tags = listOf("Uhr", "Holz", "Tradition", "Schwarzwald"),
            modelUsed = "gemini-3.8-flash"
        )

        val jsonObject = item.toJsonObject()
        val restoredItem = AnalysisItem.fromJsonObject(jsonObject)

        assertEquals("test-uuid-1234", restoredItem.id)
        assertEquals("/storage/emulated/0/wassndis/test.jpg", restoredItem.imagePath)
        assertEquals("Kuckucksuhr aus dem Schwarzwald", restoredItem.title)
        assertEquals("Eine traditionelle handgeschnitzte Holzuhr.", restoredItem.shortDescription)
        assertEquals("Die Uhr zeigt detaillierte Schnitzereien mit Blättern und einem Kuckucksvogel.", restoredItem.fullDescription)
        assertEquals(1715000000000L, restoredItem.timestamp)
        assertEquals(listOf("Uhr", "Holz", "Tradition", "Schwarzwald"), restoredItem.tags)
        assertEquals("gemini-3.8-flash", restoredItem.modelUsed)
    }

    @Test
    fun testEmptyTagsFallback() {
        val item = AnalysisItem(
            imagePath = "/path/test.jpg",
            title = "Einfaches Bild",
            shortDescription = "Kurz",
            fullDescription = "Lang"
        )
        val json = item.toJsonObject()
        val restored = AnalysisItem.fromJsonObject(json)

        assertEquals(0, restored.tags.size)
        assertEquals("gemini-3.8-flash", restored.modelUsed)
        assertEquals(0, restored.questions.size)
    }

    @Test
    fun testQuestionsSerialization() {
        val qa1 = com.example.wassndis.data.QaItem(
            id = "qa-1",
            question = "Wie alt ist dieser Gegenstand?",
            answer = "Aus den 1970er Jahren anhand des Typenschilds.",
            timestamp = 1715000100000L
        )
        val qa2 = com.example.wassndis.data.QaItem(
            id = "qa-2",
            question = "Wie viel ist es wert?",
            answer = "Etwa 50 bis 80 Euro im Sammlerzustand.",
            timestamp = 1715000200000L
        )

        val item = AnalysisItem(
            id = "item-with-qa",
            imagePath = "/path/test.jpg",
            title = "Vintage Kaffeemühle",
            shortDescription = "Eine Handkaffeemühle",
            fullDescription = "Alte mechanische Kaffeemühle",
            questions = listOf(qa1, qa2)
        )

        val json = item.toJsonObject()
        val restored = AnalysisItem.fromJsonObject(json)

        assertEquals(2, restored.questions.size)
        assertEquals("qa-1", restored.questions[0].id)
        assertEquals("Wie alt ist dieser Gegenstand?", restored.questions[0].question)
        assertEquals("Aus den 1970er Jahren anhand des Typenschilds.", restored.questions[0].answer)
        assertEquals(1715000100000L, restored.questions[0].timestamp)

        assertEquals("qa-2", restored.questions[1].id)
        assertEquals("Wie viel ist es wert?", restored.questions[1].question)
        assertEquals("Etwa 50 bis 80 Euro im Sammlerzustand.", restored.questions[1].answer)
        assertEquals(1715000200000L, restored.questions[1].timestamp)
    }
}
