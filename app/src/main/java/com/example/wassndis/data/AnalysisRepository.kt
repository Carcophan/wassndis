package com.example.wassndis.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class AnalysisRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wassndis_prefs", Context.MODE_PRIVATE)

    private val dataFile: File = File(context.filesDir, "analyses.json")
    private val imagesDir: File = File(context.filesDir, "images").apply {
        if (!exists()) mkdirs()
    }

    // --- API Key & Model Settings ---

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString("gemini_api_key", apiKey.trim()).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString("gemini_model", "gemini-3.8-flash") ?: "gemini-3.8-flash"
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString("gemini_model", model.trim()).apply()
    }

    // --- Items Storage ---

    suspend fun getItems(): List<AnalysisItem> = withContext(Dispatchers.IO) {
        if (!dataFile.exists()) return@withContext emptyList()
        try {
            val content = dataFile.readText()
            val jsonArray = JSONArray(content)
            val items = mutableListOf<AnalysisItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(AnalysisItem.fromJsonObject(obj))
            }
            items.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveItem(item: AnalysisItem): List<AnalysisItem> = withContext(Dispatchers.IO) {
        val currentItems = getItems().toMutableList()
        val index = currentItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            currentItems[index] = item
        } else {
            currentItems.add(0, item)
        }
        writeItems(currentItems)
        currentItems
    }

    suspend fun deleteItem(id: String): List<AnalysisItem> = withContext(Dispatchers.IO) {
        val currentItems = getItems().toMutableList()
        val itemToRemove = currentItems.find { it.id == id }
        if (itemToRemove != null) {
            // Delete associated image file
            try {
                val imageFile = File(itemToRemove.imagePath)
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentItems.remove(itemToRemove)
            writeItems(currentItems)
        }
        currentItems
    }

    private fun writeItems(items: List<AnalysisItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            jsonArray.put(item.toJsonObject())
        }
        dataFile.writeText(jsonArray.toString())
    }

    // --- Image File Helpers ---

    fun extractPhotoTimestamp(sourceUri: Uri): Long {
        // 1. Try MediaStore DATE_TAKEN
        try {
            val projection = arrayOf(android.provider.MediaStore.Images.Media.DATE_TAKEN)
            context.contentResolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                    if (idx != -1) {
                        val dateTaken = cursor.getLong(idx)
                        if (dateTaken > 0) return dateTaken
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Try EXIF TAG_DATETIME_ORIGINAL or TAG_DATETIME
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val exif = ExifInterface(input)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    val parsed = sdf.parse(dateStr)
                    if (parsed != null && parsed.time > 0) {
                        return parsed.time
                    }
                }
            }
        } catch (_: Exception) {}

        return System.currentTimeMillis()
    }

    fun createCameraImageUri(): Pair<Uri, File> {
        val imageFile = File(imagesDir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        return Pair(uri, imageFile)
    }

    suspend fun copyAndNormalizeImage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val destinationFile = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: throw IllegalStateException("Konnte Bild nicht dekodieren")
            
            // Adjust orientation if needed
            val orientationAdjusted = fixOrientation(sourceUri, bitmap)
            
            FileOutputStream(destinationFile).use { out ->
                orientationAdjusted.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } ?: throw IllegalStateException("Bild-InputStream konnte nicht geöffnet werden")

        destinationFile.absolutePath
    }

    private fun fixOrientation(sourceUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val input: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (input != null) {
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                input.close()
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }
}
