package com.example.wassndis

import com.example.wassndis.data.AnalysisItem
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun analysisItem_mainObjectAndDetailsSerialization_isCorrect() {
        val original = AnalysisItem(
            imagePath = "/test/path/photo.jpg",
            title = "Kaffeemühle",
            shortDescription = "Eine elektrische Kaffeemühle aus Edelstahl.",
            fullDescription = "Präzises Mahlwerk mit 15 Mahlstufen.",
            tags = listOf("Kaffee", "Küche", "Elektronik"),
            modelUsed = "gemini-3.8-flash",
            mainObject = "Graef CM 800 Kaffeemühle",
            category = "Küchenkleingerät",
            objectDetails = mapOf(
                "Material" to "Aluminium-Druckgussgehäuse",
                "Farbe & Optik" to "Silber matt",
                "Zustand" to "Gepflegt, minimale Gebrauchsspuren",
                "Funktion" to "Mahlen von Kaffeebohnen"
            )
        )

        val json = original.toJsonObject()
        val restored = AnalysisItem.fromJsonObject(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.mainObject, restored.mainObject)
        assertEquals(original.category, restored.category)
        assertEquals(original.objectDetails.size, restored.objectDetails.size)
        assertEquals("Aluminium-Druckgussgehäuse", restored.objectDetails["Material"])
        assertEquals("Silber matt", restored.objectDetails["Farbe & Optik"])
        assertEquals("Gepflegt, minimale Gebrauchsspuren", restored.objectDetails["Zustand"])
        assertEquals("Mahlen von Kaffeebohnen", restored.objectDetails["Funktion"])
    }
}