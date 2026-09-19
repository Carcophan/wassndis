package com.example.wassndis.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wassndis.data.AnalysisItem
import com.example.wassndis.data.AnalysisRepository
import com.example.wassndis.data.QaItem
import com.example.wassndis.service.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.wassndis.service.PdfReportService
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AnalysisRepository(application)
    private val geminiService = GeminiService()
    private val pdfReportService = PdfReportService(application)

    private val _items = MutableStateFlow<List<AnalysisItem>>(emptyList())
    val items: StateFlow<List<AnalysisItem>> = _items.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedItem = MutableStateFlow<AnalysisItem?>(null)
    val selectedItem: StateFlow<AnalysisItem?> = _selectedItem.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isAnsweringQuestion = MutableStateFlow(false)
    val isAnsweringQuestion: StateFlow<Boolean> = _isAnsweringQuestion.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _analysisStatus = MutableStateFlow("")
    val analysisStatus: StateFlow<String> = _analysisStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _apiKey = MutableStateFlow(repository.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(repository.getSelectedModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    init {
        loadItems()
        if (_apiKey.value.isBlank()) {
            _showSettingsDialog.value = true
        }
    }

    fun loadItems() {
        viewModelScope.launch {
            _items.value = repository.getItems()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectItem(item: AnalysisItem?) {
        _selectedItem.value = item
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun saveSettings(key: String, model: String) {
        repository.setApiKey(key)
        repository.setSelectedModel(model)
        _apiKey.value = key.trim()
        _selectedModel.value = model.trim()
        _showSettingsDialog.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createCameraUri(): Pair<Uri, File> {
        return repository.createCameraImageUri()
    }

    fun processCapturedPhoto(photoFile: File) {
        if (!photoFile.exists() || photoFile.length() == 0L) {
            _errorMessage.value = "Foto konnte nicht aufgenommen werden."
            return
        }
        val captureTimestamp = if (photoFile.lastModified() > 0) photoFile.lastModified() else System.currentTimeMillis()
        analyzeNewImage(photoFile, captureTimestamp)
    }

    fun processSelectedPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _analysisStatus.value = "Bild wird importiert..."
                val captureTimestamp = repository.extractPhotoTimestamp(uri)
                val localPath = repository.copyAndNormalizeImage(uri)
                val file = File(localPath)
                analyzeNewImage(file, captureTimestamp)
            } catch (e: Exception) {
                _isAnalyzing.value = false
                _errorMessage.value = "Fehler beim Laden des Fotos: ${e.localizedMessage}"
            }
        }
    }

    private fun analyzeNewImage(file: File, captureTimestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val key = _apiKey.value
            val model = _selectedModel.value

            if (key.isBlank()) {
                _isAnalyzing.value = false
                _showSettingsDialog.value = true
                _errorMessage.value = "Bitte gib deinen Gemini API-Key ein, um das Foto zu analysieren."
                return@launch
            }

            _isAnalyzing.value = true
            _analysisStatus.value = "$model analysiert das Foto..."

            val result = geminiService.analyzeImage(file, key, model)
            result.onSuccess { data ->
                val newItem = AnalysisItem(
                    imagePath = file.absolutePath,
                    title = data.title,
                    shortDescription = data.shortDescription,
                    fullDescription = data.fullDescription,
                    timestamp = captureTimestamp,
                    tags = data.tags,
                    modelUsed = model,
                    mainObject = data.mainObject,
                    category = data.category,
                    objectDetails = data.objectDetails
                )
                val updatedList = repository.saveItem(newItem)
                _items.value = updatedList
                _selectedItem.value = newItem // Automatically open detail view for fresh analysis!
                _isAnalyzing.value = false
            }.onFailure { error ->
                _isAnalyzing.value = false
                _errorMessage.value = error.message ?: "Unbekannter Fehler bei der Bildanalyse"
            }
        }
    }

    fun reanalyzeItem(item: AnalysisItem) {
        val file = File(item.imagePath)
        if (!file.exists()) {
            _errorMessage.value = "Bilddatei existiert nicht mehr."
            return
        }

        viewModelScope.launch {
            val key = _apiKey.value
            val model = _selectedModel.value

            if (key.isBlank()) {
                _showSettingsDialog.value = true
                return@launch
            }

            _isAnalyzing.value = true
            _analysisStatus.value = "$model analysiert das Foto erneut..."

            val result = geminiService.analyzeImage(file, key, model)
            result.onSuccess { data ->
                val updatedItem = item.copy(
                    title = data.title,
                    shortDescription = data.shortDescription,
                    fullDescription = data.fullDescription,
                    tags = data.tags,
                    modelUsed = model,
                    timestamp = item.timestamp,
                    mainObject = data.mainObject,
                    category = data.category,
                    objectDetails = data.objectDetails,
                    questions = item.questions
                )
                val updatedList = repository.saveItem(updatedItem)
                _items.value = updatedList
                if (_selectedItem.value?.id == item.id) {
                    _selectedItem.value = updatedItem
                }
                _isAnalyzing.value = false
            }.onFailure { error ->
                _isAnalyzing.value = false
                _errorMessage.value = error.message ?: "Fehler bei der Neuanalyse"
            }
        }
    }

    fun askQuestion(item: AnalysisItem, question: String) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return

        val file = File(item.imagePath)
        if (!file.exists()) {
            _errorMessage.value = "Bilddatei existiert nicht mehr."
            return
        }

        viewModelScope.launch {
            val key = _apiKey.value
            val model = _selectedModel.value

            if (key.isBlank()) {
                _showSettingsDialog.value = true
                return@launch
            }

            _isAnsweringQuestion.value = true

            val result = geminiService.askQuestionAboutImage(
                imageFile = file,
                question = trimmed,
                previousQuestions = item.questions,
                mainObject = item.mainObject.ifBlank { item.title },
                apiKey = key,
                modelName = model
            )

            result.onSuccess { answer ->
                val newQa = QaItem(
                    question = trimmed,
                    answer = answer
                )
                val updatedItem = item.copy(
                    questions = item.questions + newQa
                )
                val updatedList = repository.saveItem(updatedItem)
                _items.value = updatedList
                if (_selectedItem.value?.id == item.id) {
                    _selectedItem.value = updatedItem
                }
                _isAnsweringQuestion.value = false
            }.onFailure { error ->
                _isAnsweringQuestion.value = false
                _errorMessage.value = error.message ?: "Fehler beim Beantworten der Frage"
            }
        }
    }

    fun deleteQuestion(item: AnalysisItem, questionId: String) {
        viewModelScope.launch {
            val updatedItem = item.copy(
                questions = item.questions.filterNot { it.id == questionId }
            )
            val updatedList = repository.saveItem(updatedItem)
            _items.value = updatedList
            if (_selectedItem.value?.id == item.id) {
                _selectedItem.value = updatedItem
            }
        }
    }

    fun deleteItem(item: AnalysisItem) {
        viewModelScope.launch {
            val updatedList = repository.deleteItem(item.id)
            _items.value = updatedList
            if (_selectedItem.value?.id == item.id) {
                _selectedItem.value = null
            }
        }
    }

    fun generatePdfReport(item: AnalysisItem, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val (_, uri) = pdfReportService.generatePdf(item)
                _isGeneratingPdf.value = false
                onComplete(uri)
            } catch (e: Exception) {
                _isGeneratingPdf.value = false
                _errorMessage.value = "Fehler beim Erstellen des PDFs: ${e.localizedMessage}"
                onComplete(null)
            }
        }
    }
}
