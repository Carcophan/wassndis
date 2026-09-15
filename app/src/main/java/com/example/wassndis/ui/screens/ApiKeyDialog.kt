package com.example.wassndis.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyDialog(
    currentApiKey: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (key: String, model: String) -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var expandedModelMenu by remember { mutableStateOf(false) }

    val popularModels = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Gemini Einstellungen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Für die Bildanalyse mit Gemini wird ein Google AI API-Key benötigt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("Gemini API-Key") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Verbergen" else "Anzeigen"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://aistudio.google.com/app/apikey")
                        )
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("API-Key bei Google AI Studio holen")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Modell-Auswahl",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                ExposedDropdownMenuBox(
                    expanded = expandedModelMenu,
                    onExpandedChange = { expandedModelMenu = !expandedModelMenu }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = { Text("Modellname") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelMenu) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedModelMenu,
                        onDismissRequest = { expandedModelMenu = false }
                    ) {
                        popularModels.forEach { modelOption ->
                            DropdownMenuItem(
                                text = { Text(modelOption) },
                                onClick = {
                                    selectedModel = modelOption
                                    expandedModelMenu = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Standard: gemini-3.8-flash (schnell, hochpräzise Multimodal-Analyse).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(apiKeyText.trim(), selectedModel.trim())
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
