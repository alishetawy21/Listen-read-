package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ClinicalNoteEntity
import com.example.model.ReaderSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
  notes: List<ClinicalNoteEntity>,
  settings: ReaderSettings,
  isImporting: Boolean,
  importBanner: String?,
  onDismissImportBanner: () -> Unit,
  onOpenNote: (ClinicalNoteEntity, Boolean) -> Unit,
  onSaveNote: (Long?, String, String, String, String) -> Unit,
  onDeleteNote: (Long) -> Unit,
  onImportDocumentUri: (Uri) -> Unit,
  onImportImageUri: (Uri) -> Unit,
  onQuickReadText: (String, String) -> Unit,
  onToggleFloatingShortcut: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf("All") }
  var editingNote by remember { mutableStateOf<ClinicalNoteEntity?>(null) }
  var showNoteEditor by remember { mutableStateOf(false) }
  var showPasteReaderDialog by remember { mutableStateOf(false) }

  val pdfPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onImportDocumentUri(uri)
    }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onImportImageUri(uri)
    }

  val filteredNotes =
    remember(notes, selectedFilter) {
      when (selectedFilter) {
        "Notes" -> notes.filter { it.sourceType == "INTERNAL_NOTE" }
        "PDF & OCR" -> notes.filter { it.sourceType.contains("PDF") || it.isOcrExtracted }
        "Arabic / Multi" ->
          notes.filter {
            it.languageTag.startsWith("ar", ignoreCase = true) || it.languageTag == "auto"
          }
        else -> notes
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Hero Workspace Banner
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Image(
            painter = painterResource(id = R.drawable.img_reader_hero),
            contentDescription = "ClinNote clinical acoustic workspace banner",
            contentScale = ContentScale.Crop,
            alpha = 0.28f,
            modifier = Modifier.matchParentSize(),
          )
          Box(
            modifier =
              Modifier.matchParentSize()
                .background(
                  Brush.horizontalGradient(
                    listOf(
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
                    )
                  )
                )
          )
          Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "UNIVERSAL SMART READER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              Surface(
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleFloatingShortcut(!settings.floatingShortcutEnabled) }
                    .testTag("hero_floating_toggle"),
                color =
                  if (settings.floatingShortcutEnabled) MaterialTheme.colorScheme.tertiary
                  else Color.White.copy(alpha = 0.20f),
              ) {
                Text(
                  text =
                    if (settings.floatingShortcutEnabled) "Shortcut ON" else "Enable Shortcut",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }

            Spacer(Modifier.height(12.dp))
            Text(
              text = "ClinNote Workspace",
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                "Read clinical notes, PDFs, scanned OCR documents, or selected text from Chrome & other apps in 13+ languages.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                onClick = { showPasteReaderDialog = true },
                modifier = Modifier.testTag("quick_paste_read_button"),
              ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste & Read")
              }
              OutlinedButton(
                onClick = onOpenSettings,
                colors =
                  androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                  ),
              ) {
                Text("System Capture")
              }
            }
          }
        }
      }
    }

    // Import & Create Action Strip
    item {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            editingNote = null
            showNoteEditor = true
          },
          modifier = Modifier.testTag("new_note_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Note")
        }

        FilledTonalButton(
          onClick = {
            pdfPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
          },
          modifier = Modifier.testTag("import_pdf_button"),
        ) {
          Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Import PDF / Txt")
        }

        FilledTonalButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.testTag("scan_image_ocr_button"),
        ) {
          Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Scan Image OCR")
        }
      }
    }

    if (isImporting) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
            Text(
              "Extracting text & running on-device OCR if scanned...",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }

    if (!importBanner.isNullOrBlank()) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = importBanner,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissImportBanner) { Text("OK") }
          }
        }
      }
    }

    // Filter Chips
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("All", "Notes", "PDF & OCR", "Arabic / Multi").forEach { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(filter) },
          )
        }
      }
    }

    // Notes & Documents List
    if (filteredNotes.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              Icons.Rounded.Description,
              contentDescription = null,
              modifier = Modifier.size(40.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text("No documents in this filter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
              "Create a clinical note, import a PDF, or scan a document image to begin.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredNotes, key = { it.id }) { note ->
        ClinicalDocumentCard(
          note = note,
          onOpen = { onOpenNote(note, false) },
          onListenNow = { onOpenNote(note, true) },
          onEdit = {
            editingNote = note
            showNoteEditor = true
          },
          onDelete = { onDeleteNote(note.id) },
        )
      }
    }
  }

  if (showNoteEditor) {
    NoteEditorDialog(
      initialNote = editingNote,
      onDismiss = { showNoteEditor = false },
      onSave = { title, content, category, lang ->
        onSaveNote(editingNote?.id, title, content, category, lang)
        showNoteEditor = false
      },
    )
  }

  if (showPasteReaderDialog) {
    QuickPasteReaderDialog(
      onDismiss = { showPasteReaderDialog = false },
      onRead = { title, content ->
        showPasteReaderDialog = false
        onQuickReadText(title, content)
      },
    )
  }
}

@Composable
private fun ClinicalDocumentCard(
  note: ClinicalNoteEntity,
  onOpen: () -> Unit,
  onListenNow: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpen)
        .testTag("note_card_${note.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = note.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            if (note.isOcrExtracted) {
              Spacer(Modifier.width(6.dp))
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "OCR",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
              }
            }
            Spacer(Modifier.width(6.dp))
            Text(
              text = note.languageTag.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = note.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        FilledTonalButton(
          onClick = onListenNow,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.testTag("listen_note_${note.id}"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = "Listen to note", modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Listen")
        }
      }

      Spacer(Modifier.height(8.dp))
      Text(
        text = note.content.replace("\n", " ").trim(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit clinical note", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = "Delete clinical note",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun NoteEditorDialog(
  initialNote: ClinicalNoteEntity?,
  onDismiss: () -> Unit,
  onSave: (String, String, String, String) -> Unit,
) {
  var title by remember { mutableStateOf(initialNote?.title ?: "") }
  var category by remember { mutableStateOf(initialNote?.category ?: "Clinical Note") }
  var languageTag by remember { mutableStateOf(initialNote?.languageTag ?: "auto") }
  var content by remember { mutableStateOf(initialNote?.content ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialNote == null) "New Clinical Note" else "Edit Clinical Note") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          AssistChip(
            onClick = {
              languageTag =
                when (languageTag) {
                  "auto" -> "ar"
                  "ar" -> "en-US"
                  "en-US" -> "en-GB"
                  else -> "auto"
                }
            },
            label = { Text("Lang: $languageTag") },
          )
        }
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text("Clinical Note Text (Arabic, English, or Mixed)") },
          minLines = 5,
          maxLines = 9,
          modifier = Modifier.fillMaxWidth().testTag("note_content_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(title, content, category, languageTag) },
        enabled = content.isNotBlank(),
        modifier = Modifier.testTag("save_note_confirm_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun QuickPasteReaderDialog(
  onDismiss: () -> Unit,
  onRead: (String, String) -> Unit,
) {
  var title by remember { mutableStateOf("Quick Selection") }
  var text by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Paste & Read Text") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Paste text in Arabic, English, French, German, Spanish, etc.") },
          minLines = 5,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth().testTag("quick_paste_text_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onRead(title, text) },
        enabled = text.isNotBlank(),
        modifier = Modifier.testTag("quick_paste_start_button"),
      ) {
        Text("Read Aloud")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
