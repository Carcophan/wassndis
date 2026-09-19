package com.example.wassndis.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.wassndis.data.AnalysisItem
import com.example.wassndis.ui.components.MarkdownBlock
import com.example.wassndis.ui.components.parseMarkdownBlocks
import com.example.wassndis.ui.components.stripMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportService(private val context: Context) {

    companion object {
        // DIN A4 standard dimensions at 72 dpi (points)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842

        const val MARGIN_LEFT = 40f
        const val MARGIN_RIGHT = 555f
        const val CONTENT_WIDTH = (MARGIN_RIGHT - MARGIN_LEFT).toInt() // 515 points

        const val MARGIN_TOP = 40f
        const val MARGIN_BOTTOM = 800f
        const val MAX_CONTENT_Y = 780f // leave room for footer

        // Brand Palette (ARGB Hex Ints)
        const val COLOR_PRIMARY = 0xFF0D47A1.toInt()         // Deep Indigo #0D47A1
        const val COLOR_PRIMARY_LIGHT = 0xFFE3F2FD.toInt()   // Light Blue #E3F2FD
        const val COLOR_SECONDARY_BG = 0xFFF5F7FA.toInt()    // Soft Grey/White
        const val COLOR_CARD_BORDER = 0xFFDAE0E9.toInt()    // Light Border
        const val COLOR_TEXT_MAIN = 0xFF1A1C1E.toInt()          // Near Black
        const val COLOR_TEXT_MUTED = 0xFF5A646E.toInt()       // Slate Grey
        const val COLOR_TEXT_LABEL = 0xFF1565C0.toInt()       // Bright Blue
        const val COLOR_DIVIDER = 0xFFE0E2EC.toInt()         // Subtle Divider
        const val COLOR_TAG_BG = 0xFFEEF2F6.toInt()          // Chip Background
        const val COLOR_TAG_TEXT = 0xFF37474F.toInt()           // Chip Text
        const val COLOR_QA_USER_BG = 0xFFEDF4FF.toInt()      // Q&A Question box
        const val COLOR_QA_GEMINI_BG = 0xFFF7F9FC.toInt()    // Q&A Answer box

        fun sanitizeFilename(rawTitle: String, timestamp: Long): String {
            val safeTitle = rawTitle.trim()
                .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß\\-_]"), "_")
                .replace(Regex("_{2,}"), "_")
                .trim('_')
                .take(35)
                .ifBlank { "Gegenstand" }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date(timestamp))
            return "Bericht_${safeTitle}_$dateStr.pdf"
        }
    }

    private val pdfOutputDir = File(context.cacheDir, "pdfs").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Erstellt einen mehrseitigen PDF-Bericht für das übergebene AnalysisItem.
     * Gibt die erzeugte File sowie die freigabefähige Content-Uri zurück.
     */
    suspend fun generatePdf(item: AnalysisItem): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val fileName = sanitizeFilename(item.mainObject.ifBlank { item.title }, item.timestamp)
        val outputFile = File(pdfOutputDir, fileName)

        val document = PdfDocument()

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_MAIN
            textSize = 10f
        }

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_DIVIDER
            strokeWidth = 1f
        }

        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Layout coordinator
        val engine = PdfLayoutEngine(
            document = document,
            item = item,
            textPaint = textPaint,
            linePaint = linePaint,
            fillPaint = fillPaint,
            context = context
        )

        engine.buildDocument()

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )

        Pair(outputFile, uri)
    }

    private class PdfLayoutEngine(
        private val document: PdfDocument,
        private val item: AnalysisItem,
        private val textPaint: TextPaint,
        private val linePaint: Paint,
        private val fillPaint: Paint,
        private val context: Context
    ) {
        private var currentPageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var currentY = MARGIN_TOP

        private val formattedDate: String = SimpleDateFormat("dd. MMMM yyyy, HH:mm 'Uhr'", Locale.GERMANY)
            .format(Date(item.timestamp))

        fun buildDocument() {
            startNewPage()

            // 1. First Page Header
            drawFirstPageHeader()

            // 2. Main Object Card with Photo and Summary
            drawObjectOverviewCard()

            // 3. Object details (if present)
            if (item.objectDetails.isNotEmpty()) {
                drawObjectDetailsSection()
            }

            // 4. Tags
            if (item.tags.isNotEmpty()) {
                drawTagsSection()
            }

            // 5. Full explanation & function
            if (item.fullDescription.isNotBlank()) {
                drawFullDescriptionSection()
            }

            // 6. Questions and answers
            if (item.questions.isNotEmpty()) {
                drawQuestionsSection()
            }

            // Finish the last page
            finishCurrentPage()
        }

        private fun startNewPage() {
            finishCurrentPage()
            currentPageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas

            if (currentPageNumber > 1) {
                drawRunningHeader()
                currentY = MARGIN_TOP + 25f
            } else {
                currentY = MARGIN_TOP
            }
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return
            val c = canvas ?: return

            drawFooter(c, currentPageNumber)
            document.finishPage(page)
            currentPage = null
            canvas = null
        }

        private fun ensureSpace(heightNeeded: Float) {
            if (currentY + heightNeeded > MAX_CONTENT_Y) {
                startNewPage()
            }
        }

        private fun drawRunningHeader() {
            val c = canvas ?: return
            textPaint.apply {
                textSize = 8.5f
                typeface = Typeface.DEFAULT
                color = COLOR_TEXT_MUTED
            }
            val titleSnippet = (item.mainObject.ifBlank { item.title }).take(50)
            c.drawText("Что это? • $titleSnippet", MARGIN_LEFT, MARGIN_TOP + 10f, textPaint)

            linePaint.color = COLOR_DIVIDER
            linePaint.strokeWidth = 0.75f
            c.drawLine(MARGIN_LEFT, MARGIN_TOP + 16f, MARGIN_RIGHT, MARGIN_TOP + 16f, linePaint)
        }

        private fun drawFooter(c: Canvas, pageNum: Int) {
            linePaint.color = COLOR_DIVIDER
            linePaint.strokeWidth = 0.75f
            c.drawLine(MARGIN_LEFT, 805f, MARGIN_RIGHT, 805f, linePaint)

            textPaint.apply {
                textSize = 8f
                typeface = Typeface.DEFAULT
                color = COLOR_TEXT_MUTED
            }
            c.drawText("Erstellt mit Что это? • Modell: ${item.modelUsed}", MARGIN_LEFT, 818f, textPaint)

            val pageStr = "Seite $pageNum"
            val pageStrWidth = textPaint.measureText(pageStr)
            c.drawText(pageStr, MARGIN_RIGHT - pageStrWidth, 818f, textPaint)
        }

        private fun drawFirstPageHeader() {
            val c = canvas ?: return

            // Top Bar Brand Badge
            fillPaint.color = COLOR_PRIMARY
            val brandRect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + 70f, currentY + 16f)
            c.drawRoundRect(brandRect, 4f, 4f, fillPaint)

            textPaint.apply {
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.WHITE
            }
            c.drawText("Что это?", MARGIN_LEFT + 10f, currentY + 11.5f, textPaint)

            textPaint.apply {
                textSize = 8.5f
                typeface = Typeface.DEFAULT
                color = COLOR_TEXT_MUTED
            }
            c.drawText("KI-Objektanalyse & Erkennungsbericht", MARGIN_LEFT + 80f, currentY + 11.5f, textPaint)

            val dateWidth = textPaint.measureText(formattedDate)
            c.drawText(formattedDate, MARGIN_RIGHT - dateWidth, currentY + 11.5f, textPaint)

            currentY += 24f

            // Document Title
            textPaint.apply {
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_PRIMARY
            }
            val titleLayout = createStaticLayout(item.title, textPaint, CONTENT_WIDTH)
            c.save()
            c.translate(MARGIN_LEFT, currentY)
            titleLayout.draw(c)
            c.restore()
            currentY += titleLayout.height + 8f

            // Top divider
            linePaint.color = COLOR_PRIMARY
            linePaint.strokeWidth = 1.5f
            c.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
            currentY += 12f
        }

        private fun drawObjectOverviewCard() {
            val c = canvas ?: return

            val imageFile = File(item.imagePath)
            val hasImage = imageFile.exists() && imageFile.length() > 0

            val photoWidth = 160f
            val photoHeight = 140f
            val textColumnX = if (hasImage) MARGIN_LEFT + photoWidth + 14f else MARGIN_LEFT + 14f
            val textColumnWidth = if (hasImage) CONTENT_WIDTH - photoWidth.toInt() - 28 else CONTENT_WIDTH - 28

            // Pre-calculate content height for text column
            var innerY = 12f

            // Main object label & value
            textPaint.apply {
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_TEXT_LABEL
            }
            innerY += 12f

            textPaint.apply {
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_TEXT_MAIN
            }
            val mainObjText = item.mainObject.ifBlank { item.title }
            val mainObjLayout = createStaticLayout(mainObjText, textPaint, textColumnWidth)
            innerY += mainObjLayout.height + 6f

            // Category badge height if present
            if (item.category.isNotBlank()) {
                innerY += 18f
            }

            // Short description
            val shortDescText = stripMarkdown(item.shortDescription)
            val shortDescLayout = if (shortDescText.isNotBlank()) {
                textPaint.apply {
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT
                    color = COLOR_TEXT_MUTED
                }
                val layout = createStaticLayout(shortDescText, textPaint, textColumnWidth)
                innerY += layout.height + 10f
                layout
            } else null

            val calculatedHeight = maxOf(if (hasImage) photoHeight + 20f else 60f, innerY + 12f)
            ensureSpace(calculatedHeight)

            val activeCanvas = canvas ?: return

            // Draw Card background & border
            fillPaint.color = COLOR_SECONDARY_BG
            val cardRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + calculatedHeight)
            activeCanvas.drawRoundRect(cardRect, 8f, 8f, fillPaint)

            linePaint.color = COLOR_CARD_BORDER
            linePaint.strokeWidth = 1f
            linePaint.style = Paint.Style.STROKE
            activeCanvas.drawRoundRect(cardRect, 8f, 8f, linePaint)
            linePaint.style = Paint.Style.FILL

            // Draw Photo on left
            if (hasImage) {
                try {
                    val bitmap = decodeSampledBitmap(imageFile.absolutePath, 400, 350)
                    if (bitmap != null) {
                        val imgDest = RectF(
                            MARGIN_LEFT + 10f,
                            currentY + 10f,
                            MARGIN_LEFT + 10f + photoWidth,
                            currentY + 10f + photoHeight
                        )
                        drawScaledBitmapInRect(activeCanvas, bitmap, imgDest)
                    }
                } catch (_: Exception) {
                    // Ignore photo error and continue
                }
            }

            // Draw texts inside Card
            var curTextY = currentY + 14f

            textPaint.apply {
                textSize = 7.5f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_TEXT_LABEL
            }
            activeCanvas.drawText("HAUPTGEGENSTAND IM FOKUS", textColumnX, curTextY, textPaint)
            curTextY += 13f

            textPaint.apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_TEXT_MAIN
            }
            activeCanvas.save()
            activeCanvas.translate(textColumnX, curTextY)
            mainObjLayout.draw(activeCanvas)
            activeCanvas.restore()
            curTextY += mainObjLayout.height + 6f

            if (item.category.isNotBlank()) {
                // Category pill
                textPaint.apply {
                    textSize = 8f
                    typeface = Typeface.DEFAULT_BOLD
                    color = COLOR_PRIMARY
                }
                val catWidth = textPaint.measureText(item.category)
                fillPaint.color = COLOR_PRIMARY_LIGHT
                val pillRect = RectF(textColumnX, curTextY, textColumnX + catWidth + 12f, curTextY + 14f)
                activeCanvas.drawRoundRect(pillRect, 4f, 4f, fillPaint)
                activeCanvas.drawText(item.category, textColumnX + 6f, curTextY + 10f, textPaint)
                curTextY += 20f
            }

            if (shortDescLayout != null) {
                activeCanvas.save()
                activeCanvas.translate(textColumnX, curTextY)
                shortDescLayout.draw(activeCanvas)
                activeCanvas.restore()
            }

            currentY += calculatedHeight + 14f
        }

        private fun drawObjectDetailsSection() {
            ensureSpace(50f)
            drawSectionHeader("Gegenstandsmerkmale & Details")

            val labelWidth = 140
            val valueWidth = CONTENT_WIDTH - labelWidth - 16

            item.objectDetails.forEach { (key, rawValue) ->
                val value = stripMarkdown(rawValue)

                textPaint.apply {
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = COLOR_TEXT_MAIN
                }
                val keyLayout = createStaticLayout(key, textPaint, labelWidth)

                textPaint.apply {
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT
                    color = COLOR_TEXT_MAIN
                }
                val valueLayout = createStaticLayout(value, textPaint, valueWidth)

                val rowHeight = maxOf(keyLayout.height, valueLayout.height) + 8f
                ensureSpace(rowHeight)

                val activeCanvas = canvas ?: return

                // Subtle alternating row background
                fillPaint.color = COLOR_SECONDARY_BG
                val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight)
                activeCanvas.drawRoundRect(rowRect, 4f, 4f, fillPaint)

                // Key
                activeCanvas.save()
                activeCanvas.translate(MARGIN_LEFT + 8f, currentY + 4f)
                keyLayout.draw(activeCanvas)
                activeCanvas.restore()

                // Value
                activeCanvas.save()
                activeCanvas.translate(MARGIN_LEFT + labelWidth + 8f, currentY + 4f)
                valueLayout.draw(activeCanvas)
                activeCanvas.restore()

                currentY += rowHeight + 3f
            }

            currentY += 10f
        }

        private fun drawTagsSection() {
            ensureSpace(30f)

            var tagX = MARGIN_LEFT
            val tagHeight = 15f

            textPaint.apply {
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_TEXT_MUTED
            }
            canvas?.drawText("Tags:", tagX, currentY + 11f, textPaint)
            tagX += textPaint.measureText("Tags:") + 8f

            textPaint.apply {
                textSize = 8f
                typeface = Typeface.DEFAULT
                color = COLOR_TAG_TEXT
            }

            item.tags.forEach { tag ->
                val label = if (tag.startsWith("#")) tag else "#$tag"
                val textW = textPaint.measureText(label)
                val pillW = textW + 10f

                if (tagX + pillW > MARGIN_RIGHT) {
                    currentY += tagHeight + 4f
                    ensureSpace(tagHeight + 4f)
                    tagX = MARGIN_LEFT
                }

                val activeCanvas = canvas ?: return
                fillPaint.color = COLOR_TAG_BG
                val pillRect = RectF(tagX, currentY, tagX + pillW, currentY + tagHeight)
                activeCanvas.drawRoundRect(pillRect, 4f, 4f, fillPaint)
                activeCanvas.drawText(label, tagX + 5f, currentY + 10.5f, textPaint)

                tagX += pillW + 6f
            }

            currentY += tagHeight + 12f
        }

        private fun drawFullDescriptionSection() {
            ensureSpace(50f)
            drawSectionHeader("Gegenstands-Erklärung & Funktion")

            val blocks = parseMarkdownBlocks(item.fullDescription)
            if (blocks.isEmpty()) {
                val layout = createStaticLayout(item.fullDescription, textPaint, CONTENT_WIDTH)
                ensureSpace(layout.height + 10f)
                canvas?.let { c ->
                    c.save()
                    c.translate(MARGIN_LEFT, currentY)
                    layout.draw(c)
                    c.restore()
                }
                currentY += layout.height + 14f
                return
            }

            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val size = when (block.level) {
                            1 -> 13f
                            2 -> 12f
                            3 -> 11f
                            else -> 10.5f
                        }
                        textPaint.apply {
                            textSize = size
                            typeface = Typeface.DEFAULT_BOLD
                            color = COLOR_PRIMARY
                        }
                        val layout = createStaticLayout(stripMarkdown(block.text), textPaint, CONTENT_WIDTH)
                        ensureSpace(layout.height + 12f)
                        currentY += 4f
                        canvas?.let { c ->
                            c.save()
                            c.translate(MARGIN_LEFT, currentY)
                            layout.draw(c)
                            c.restore()
                        }
                        currentY += layout.height + 4f
                    }

                    is MarkdownBlock.BulletItem -> {
                        textPaint.apply {
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT
                            color = COLOR_TEXT_MAIN
                        }
                        val bulletLayout = createStaticLayout(stripMarkdown(block.text), textPaint, CONTENT_WIDTH - 16)
                        ensureSpace(bulletLayout.height + 4f)
                        canvas?.let { c ->
                            fillPaint.color = COLOR_PRIMARY
                            c.drawCircle(MARGIN_LEFT + 5f, currentY + 6.5f, 2f, fillPaint)
                            c.save()
                            c.translate(MARGIN_LEFT + 14f, currentY)
                            bulletLayout.draw(c)
                            c.restore()
                        }
                        currentY += bulletLayout.height + 4f
                    }

                    is MarkdownBlock.NumberedItem -> {
                        textPaint.apply {
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT
                            color = COLOR_TEXT_MAIN
                        }
                        val numLayout = createStaticLayout(stripMarkdown(block.text), textPaint, CONTENT_WIDTH - 20)
                        ensureSpace(numLayout.height + 4f)
                        canvas?.let { c ->
                            textPaint.typeface = Typeface.DEFAULT_BOLD
                            c.drawText("${block.number}.", MARGIN_LEFT, currentY + 9.5f, textPaint)
                            textPaint.typeface = Typeface.DEFAULT
                            c.save()
                            c.translate(MARGIN_LEFT + 18f, currentY)
                            numLayout.draw(c)
                            c.restore()
                        }
                        currentY += numLayout.height + 4f
                    }

                    is MarkdownBlock.Blockquote -> {
                        textPaint.apply {
                            textSize = 9.5f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            color = COLOR_TEXT_MUTED
                        }
                        val quoteLayout = createStaticLayout(stripMarkdown(block.text), textPaint, CONTENT_WIDTH - 20)
                        ensureSpace(quoteLayout.height + 8f)
                        canvas?.let { c ->
                            linePaint.color = COLOR_PRIMARY
                            linePaint.strokeWidth = 2.5f
                            c.drawLine(MARGIN_LEFT + 2f, currentY, MARGIN_LEFT + 2f, currentY + quoteLayout.height, linePaint)
                            c.save()
                            c.translate(MARGIN_LEFT + 12f, currentY)
                            quoteLayout.draw(c)
                            c.restore()
                        }
                        currentY += quoteLayout.height + 8f
                    }

                    is MarkdownBlock.CodeBlock -> {
                        textPaint.apply {
                            textSize = 8.5f
                            typeface = Typeface.MONOSPACE
                            color = COLOR_TEXT_MAIN
                        }
                        val codeLayout = createStaticLayout(block.code, textPaint, CONTENT_WIDTH - 16)
                        ensureSpace(codeLayout.height + 12f)
                        canvas?.let { c ->
                            fillPaint.color = COLOR_SECONDARY_BG
                            val bg = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + codeLayout.height + 8f)
                            c.drawRoundRect(bg, 4f, 4f, fillPaint)
                            c.save()
                            c.translate(MARGIN_LEFT + 8f, currentY + 4f)
                            codeLayout.draw(c)
                            c.restore()
                        }
                        currentY += codeLayout.height + 12f
                    }

                    is MarkdownBlock.Paragraph -> {
                        textPaint.apply {
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT
                            color = COLOR_TEXT_MAIN
                        }
                        val pLayout = createStaticLayout(stripMarkdown(block.text), textPaint, CONTENT_WIDTH)
                        ensureSpace(pLayout.height + 6f)
                        canvas?.let { c ->
                            c.save()
                            c.translate(MARGIN_LEFT, currentY)
                            pLayout.draw(c)
                            c.restore()
                        }
                        currentY += pLayout.height + 6f
                    }

                    is MarkdownBlock.Divider -> {
                        ensureSpace(10f)
                        canvas?.let { c ->
                            linePaint.color = COLOR_DIVIDER
                            linePaint.strokeWidth = 0.8f
                            c.drawLine(MARGIN_LEFT, currentY + 4f, MARGIN_RIGHT, currentY + 4f, linePaint)
                        }
                        currentY += 10f
                    }
                }
            }

            currentY += 12f
        }

        private fun drawQuestionsSection() {
            ensureSpace(50f)
            drawSectionHeader("Fragen & Antworten zum Gegenstand (${item.questions.size})")

            item.questions.forEach { qa ->
                val qDate = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY).format(Date(qa.timestamp))

                // Question text
                textPaint.apply {
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = COLOR_PRIMARY
                }
                val qLayout = createStaticLayout("Frage: ${qa.question}", textPaint, CONTENT_WIDTH - 20)

                // Answer text
                textPaint.apply {
                    textSize = 9f
                    typeface = Typeface.DEFAULT
                    color = COLOR_TEXT_MAIN
                }
                val answerClean = stripMarkdown(qa.answer)
                val aLayout = createStaticLayout(answerClean, textPaint, CONTENT_WIDTH - 20)

                val boxHeight = qLayout.height + aLayout.height + 34f
                ensureSpace(boxHeight)

                val activeCanvas = canvas ?: return

                // Box border & background
                fillPaint.color = COLOR_QA_USER_BG
                val boxRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + boxHeight)
                activeCanvas.drawRoundRect(boxRect, 6f, 6f, fillPaint)

                linePaint.color = COLOR_CARD_BORDER
                linePaint.strokeWidth = 0.8f
                linePaint.style = Paint.Style.STROKE
                activeCanvas.drawRoundRect(boxRect, 6f, 6f, linePaint)
                linePaint.style = Paint.Style.FILL

                // Question
                activeCanvas.save()
                activeCanvas.translate(MARGIN_LEFT + 10f, currentY + 8f)
                qLayout.draw(activeCanvas)
                activeCanvas.restore()

                // Divider inside Q&A card
                val midY = currentY + 8f + qLayout.height + 4f
                linePaint.color = COLOR_CARD_BORDER
                linePaint.strokeWidth = 0.6f
                activeCanvas.drawLine(MARGIN_LEFT + 10f, midY, MARGIN_RIGHT - 10f, midY, linePaint)

                // Answer Header (Gemini icon / label)
                textPaint.apply {
                    textSize = 7.5f
                    typeface = Typeface.DEFAULT_BOLD
                    color = COLOR_TEXT_MUTED
                }
                activeCanvas.drawText("Antwort (Gemini • $qDate):", MARGIN_LEFT + 10f, midY + 11f, textPaint)

                // Answer
                activeCanvas.save()
                activeCanvas.translate(MARGIN_LEFT + 10f, midY + 16f)
                aLayout.draw(activeCanvas)
                activeCanvas.restore()

                currentY += boxHeight + 10f
            }
        }

        private fun drawSectionHeader(title: String) {
            val c = canvas ?: return

            textPaint.apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                color = COLOR_PRIMARY
            }
            c.drawText(title, MARGIN_LEFT, currentY + 12f, textPaint)

            linePaint.color = COLOR_PRIMARY
            linePaint.strokeWidth = 1f
            c.drawLine(MARGIN_LEFT, currentY + 17f, MARGIN_LEFT + 40f, currentY + 17f, linePaint)

            linePaint.color = COLOR_DIVIDER
            linePaint.strokeWidth = 0.6f
            c.drawLine(MARGIN_LEFT + 40f, currentY + 17f, MARGIN_RIGHT, currentY + 17f, linePaint)

            currentY += 26f
        }

        private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(10, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        }

        private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)

                var inSampleSize = 1
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2
                    }
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                BitmapFactory.decodeFile(path, options)
            } catch (e: Exception) {
                null
            }
        }

        private fun drawScaledBitmapInRect(canvas: Canvas, bitmap: Bitmap, destRect: RectF) {
            val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val destAspect = destRect.width() / destRect.height()

            val drawRect = if (srcAspect > destAspect) {
                // Wider than dest
                val scaledHeight = destRect.width() / srcAspect
                val dy = (destRect.height() - scaledHeight) / 2f
                RectF(destRect.left, destRect.top + dy, destRect.right, destRect.top + dy + scaledHeight)
            } else {
                // Taller than dest
                val scaledWidth = destRect.height() * srcAspect
                val dx = (destRect.width() - scaledWidth) / 2f
                RectF(destRect.left + dx, destRect.top, destRect.left + dx + scaledWidth, destRect.bottom)
            }

            // Draw image with rounded corners
            canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(drawRect, 6f, 6f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bitmap, null, drawRect, null)
            canvas.restore()

            // Outer border around image
            linePaint.color = COLOR_CARD_BORDER
            linePaint.strokeWidth = 1f
            linePaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(drawRect, 6f, 6f, linePaint)
            linePaint.style = Paint.Style.FILL
        }
    }
}
