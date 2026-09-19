package com.example.wassndis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.wassndis.ui.MainViewModel
import com.example.wassndis.ui.screens.ApiKeyDialog
import com.example.wassndis.ui.screens.DetailScreen
import com.example.wassndis.ui.screens.OverviewScreen
import com.example.wassndis.ui.theme.WassndisTheme
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.example.wassndis.ACTION_SCAN"
        const val EXTRA_QUICK_CAPTURE = "extra_quick_capture"
    }

    private val viewModel: MainViewModel by viewModels()

    // Temporary storage for current camera capture
    private var currentCameraFile: File? = null

    // Flag for direct camera launch
    private var pendingQuickCapture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            checkIntentForQuickCapture(intent)
        }

        setContent {
            WassndisTheme {
                val items by viewModel.items.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val selectedItem by viewModel.selectedItem.collectAsState()
                val isAnalyzing by viewModel.isAnalyzing.collectAsState()
                val isAnsweringQuestion by viewModel.isAnsweringQuestion.collectAsState()
                val analysisStatus by viewModel.analysisStatus.collectAsState()
                val errorMessage by viewModel.errorMessage.collectAsState()
                val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
                val apiKey by viewModel.apiKey.collectAsState()
                val selectedModel by viewModel.selectedModel.collectAsState()
                val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }

                // Camera launcher
                val takePictureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        currentCameraFile?.let { file ->
                            viewModel.processCapturedPhoto(file)
                        }
                    }
                }

                // Helper to launch camera capture
                val launchCamera = {
                    try {
                        val (uri, file) = viewModel.createCameraUri()
                        currentCameraFile = file
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Kamera konnte nicht gestartet werden: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Quick capture trigger from lock screen / Quick Settings tile / shortcut
                LaunchedEffect(pendingQuickCapture) {
                    if (pendingQuickCapture) {
                        pendingQuickCapture = false
                        launchCamera()
                    }
                }

                // Gallery picker launcher
                val pickMediaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    uri?.let {
                        viewModel.processSelectedPhoto(it)
                    }
                }

                // Handle error messages with Snackbar
                LaunchedEffect(errorMessage) {
                    errorMessage?.let { msg ->
                        val result = snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = if (apiKey.isBlank()) "Einstellungen" else "OK",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed && apiKey.isBlank()) {
                            viewModel.setShowSettingsDialog(true)
                        }
                        viewModel.clearError()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedItem,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { targetItem ->
                        if (targetItem != null) {
                            DetailScreen(
                                item = targetItem,
                                isAnalyzing = isAnalyzing,
                                isAnsweringQuestion = isAnsweringQuestion,
                                isGeneratingPdf = isGeneratingPdf,
                                onBack = { viewModel.selectItem(null) },
                                onDelete = { viewModel.deleteItem(targetItem) },
                                onReanalyze = { viewModel.reanalyzeItem(targetItem) },
                                onAskQuestion = { question -> viewModel.askQuestion(targetItem, question) },
                                onDeleteQuestion = { questionId -> viewModel.deleteQuestion(targetItem, questionId) },
                                onGeneratePdf = { onComplete -> viewModel.generatePdfReport(targetItem, onComplete) }
                            )
                        } else {
                            OverviewScreen(
                                items = items,
                                searchQuery = searchQuery,
                                isAnalyzing = isAnalyzing,
                                analysisStatus = analysisStatus,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onItemClick = { viewModel.selectItem(it) },
                                onDeleteItem = { viewModel.deleteItem(it) },
                                onCapturePhoto = launchCamera,
                                onPickFromGallery = {
                                    pickMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onOpenSettings = {
                                    viewModel.setShowSettingsDialog(true)
                                }
                            )
                        }
                    }
                }

                // API Key & Model Settings Dialog
                if (showSettingsDialog) {
                    ApiKeyDialog(
                        currentApiKey = apiKey,
                        currentModel = selectedModel,
                        onDismiss = { viewModel.setShowSettingsDialog(false) },
                        onSave = { key, model ->
                            viewModel.saveSettings(key, model)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForQuickCapture(intent)
    }

    private fun checkIntentForQuickCapture(intent: Intent?) {
        if (intent == null) return
        val isQuickCapture = intent.getBooleanExtra(EXTRA_QUICK_CAPTURE, false) ||
                intent.action == ACTION_QUICK_CAPTURE ||
                intent.action == "android.media.action.IMAGE_CAPTURE"
        if (isQuickCapture) {
            pendingQuickCapture = true
        }
    }
}