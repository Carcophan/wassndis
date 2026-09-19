package com.example.wassndis

import com.example.wassndis.service.PdfReportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReportTest {

    @Test
    fun testSanitizeFilename() {
        val timestamp = 1715000000000L // 2024-05-06
        val filename = PdfReportService.sanitizeFilename("Kuckucksuhr / Schwarzwald: Modell #1!", timestamp)

        assertTrue(filename.startsWith("Bericht_Kuckucksuhr_Schwarzwald_Modell_1_"))
        assertTrue(filename.endsWith(".pdf"))
        // Check no illegal characters
        assertTrue(!filename.contains("/"))
        assertTrue(!filename.contains(":"))
        assertTrue(!filename.contains("#"))
        assertTrue(!filename.contains("!"))
        assertTrue(!filename.contains(" "))
    }

    @Test
    fun testSanitizeFilenameEmptyFallback() {
        val timestamp = 1715000000000L
        val filename = PdfReportService.sanitizeFilename("   /// :::   ", timestamp)

        assertTrue(filename.startsWith("Bericht_Gegenstand_"))
        assertTrue(filename.endsWith(".pdf"))
    }
}
