/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.AudioClip
import com.google.ai.edge.gallery.common.convertWavToMonoWithMaxSeconds
import com.google.ai.edge.gallery.common.decodeSampledBitmapFromUri
import com.google.ai.edge.gallery.common.rotateBitmap
import com.google.ai.edge.gallery.data.MAX_AUDIO_CLIP_COUNT
import com.google.ai.edge.gallery.data.MAX_IMAGE_COUNT
import com.google.ai.edge.gallery.data.MAX_IMAGE_COUNT_AI_CORE
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.common.chat.rag.AttachmentScope
import com.google.ai.edge.gallery.ui.common.chat.rag.DocumentAttachment
import com.google.ai.edge.gallery.ui.common.chat.rag.DocumentIndexingStatus
import com.google.ai.edge.gallery.ui.common.chat.rag.DocumentTextExtractor
import com.google.ai.edge.gallery.ui.common.chat.rag.PendingRagStaging
import com.google.ai.edge.gallery.ui.theme.bodyLargeNarrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.concurrent.Executors

private const val TAG = "AGMessageInputText"

/**
 * Composable function to display a text input field for composing chat messages.
 *
 * This function renders a row containing a text field for message input and a send button. It
 * handles message composition, input validation, and sending messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputText(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  curMessage: String,
  isResettingSession: Boolean,
  inProgress: Boolean,
  imageCount: Int,
  audioClipMessageCount: Int,
  modelInitializing: Boolean,
  @StringRes textFieldPlaceHolderRes: Int,
  onValueChanged: (String) -> Unit,
  onSendMessage: (List<ChatMessage>) -> Unit,
  modelPreparing: Boolean = false,
  onOpenPromptTemplatesClicked: () -> Unit = {},
  onStopButtonClicked: () -> Unit = {},
  onSetAudioRecorderVisible: (visible: Boolean) -> Unit = {},
  onAmplitudeChanged: (Int) -> Unit,
  onSkillsClicked: () -> Unit = {},
  onPickedImagesChanged: (List<Bitmap>) -> Unit = {},
  onPickedAudioClipsChanged: (List<AudioClip>) -> Unit = {},
  showPromptTemplatesInMenu: Boolean = false,
  showSkillsPicker: Boolean = false,
  showImagePicker: Boolean = false,
  showAudioPicker: Boolean = false,
  showStopButtonWhenInProgress: Boolean = false,
  onImageLimitExceeded: () -> Unit = {},
  onConversationModeClicked: () -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  var showAddContentMenu by remember { mutableStateOf(false) }
  var showTextInputHistorySheet by remember { mutableStateOf(false) }
  var showCameraCaptureBottomSheet by remember { mutableStateOf(false) }
  val cameraCaptureSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showAudioRecorder by remember { mutableStateOf(false) }
  val audioRecorderSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var pickedImages by remember { mutableStateOf<List<Bitmap>>(listOf()) }
  var pickedAudioClips by remember { mutableStateOf<List<AudioClip>>(listOf()) }
  // --- Document attachments (RAG) -------------------------------------------------
  // Documents (text-bearing files) the user has attached. Each carries the scope
  // (PERSISTENT -> on-disk RAG, TEMPORARY -> ephemeral per-request RAG).
  var pickedDocuments by remember { mutableStateOf<List<DocumentAttachment>>(listOf()) }
  // Scope of the next document picked. Set just before launching the picker.
  var nextDocumentScope by remember { mutableStateOf(AttachmentScope.TEMPORARY) }
  var hasFrontCamera by remember { mutableStateOf(false) }
  val sensorObserver = remember { SensorObserver(context) }

  // Hold-to-talk state.
  var isHoldRecording by remember { mutableStateOf(false) }
  val holdAudioRecordState = remember { mutableStateOf<AudioRecord?>(null) }
  val holdAudioStream = remember { java.io.ByteArrayOutputStream() }

  // Tap-to-record (context) state. Declared at parent scope so it can be
  // released on dispose and cleanly stopped when switching to other modes
  // (e.g. conversation mode) independently from hold-to-talk.
  var isContextRecording by remember { mutableStateOf(false) }
  val contextAudioRecordState = remember { mutableStateOf<AudioRecord?>(null) }
  val contextAudioStream = remember { java.io.ByteArrayOutputStream() }

  // Clean up BOTH recorders on dispose. This guarantees the microphone is
  // freed when the composable leaves the tree (e.g. when the user switches
  // to conversation mode, which replaces this input with AudioRecorderPanel).
  DisposableEffect(Unit) {
    onDispose {
      try {
        holdAudioRecordState.value?.let { rec ->
          if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
          rec.release()
        }
      } catch (_: Throwable) {}
      holdAudioRecordState.value = null
      try {
        contextAudioRecordState.value?.let { rec ->
          if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
          rec.release()
        }
      } catch (_: Throwable) {}
      contextAudioRecordState.value = null
    }
  }

  // Helper to fully stop any ongoing hold/context recording without sending.
  // Used when transitioning to the other recording mode (conversation mode).
  val stopAnyActiveRecording: () -> Unit = {
    if (isHoldRecording) {
      stopHoldToTalkRecording(holdAudioRecordState, holdAudioStream)
      isHoldRecording = false
    }
    if (isContextRecording) {
      stopHoldToTalkRecording(contextAudioRecordState, contextAudioStream)
      isContextRecording = false
    }
    // Always clear both buffers so the next session starts fresh regardless
    // of which mode was active.
    holdAudioStream.reset()
    contextAudioStream.reset()
    onSetAudioRecorderVisible(false)
  }

  val updatePickedImages: (List<Bitmap>) -> Unit = { bitmaps ->
    val isAiCore = modelManagerUiState.selectedModel.runtimeType == RuntimeType.AICORE
    var limit = MAX_IMAGE_COUNT
    if (isAiCore) {
      limit = MAX_IMAGE_COUNT_AI_CORE
    }
    val maxAllowedForThisMessage = (limit - imageCount).coerceAtLeast(0)

    val combinedSize = pickedImages.size + bitmaps.size
    val withinLimit = combinedSize <= maxAllowedForThisMessage

    pickedImages =
      if (withinLimit) {
        pickedImages + bitmaps
      } else {
        if (isAiCore) {
          scope.launch(Dispatchers.Main) { onImageLimitExceeded() }
        }
        (pickedImages + bitmaps).take(maxAllowedForThisMessage)
      }
  }

  val updatePickedAudioClips: (List<AudioClip>) -> Unit = { audioDataList ->
    val maxAllowedForThisMessage = (MAX_AUDIO_CLIP_COUNT - audioClipMessageCount).coerceAtLeast(0)

    val combinedSize = pickedAudioClips.size + audioDataList.size
    val withinLimit = combinedSize <= maxAllowedForThisMessage

    pickedAudioClips =
      if (withinLimit) {
        pickedAudioClips + audioDataList
      } else {
        (pickedAudioClips + audioDataList).take(maxAllowedForThisMessage)
      }
  }

  LaunchedEffect(Unit) { checkFrontCamera(context = context, callback = { hasFrontCamera = it }) }

  LaunchedEffect(pickedImages) { onPickedImagesChanged(pickedImages) }

  LaunchedEffect(pickedAudioClips) { onPickedAudioClipsChanged(pickedAudioClips) }

  // Permission request when taking picture.
  val takePicturePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      permissionGranted ->
      if (permissionGranted) {
        showAddContentMenu = false
        showCameraCaptureBottomSheet = true
      }
    }

  val handleClickRecordAudioClip = {
    showAddContentMenu = false
    showAudioRecorder = true
    onSetAudioRecorderVisible(true)
  }

  // Permission request when recording audio clips.
  val recordAudioClipsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      permissionGranted ->
      if (permissionGranted) {
        handleClickRecordAudioClip()
      }
    }

  // Registers a photo picker activity launcher in single-select mode.
  val pickMedia =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
      // Callback is invoked after the user selects media items or closes the
      // photo picker.
      if (uris.isNotEmpty()) {
        scope.launch(Dispatchers.IO) {
          handleImagesSelected(
            context = context,
            uris = uris,
            onImagesSelected = { bitmaps -> updatePickedImages(bitmaps) },
          )
        }
      }
    }

  val pickWav =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          Log.d(TAG, "Picked wav file: $uri")
          scope.launch(Dispatchers.IO) {
            handleAudioWavSelected(
              context = context,
              uri = uri,
              onAudioSelected = { audioClip ->
                updatePickedAudioClips(
                  listOf(
                    AudioClip(audioData = audioClip.audioData, sampleRate = audioClip.sampleRate)
                  )
                )
              },
            )
          }
        }
      } else {
        Log.d(TAG, "Wav picking cancelled.")
      }
    }

  // Registers a document picker. Can pick multiple text-bearing files.
  // The scope of the resulting attachments is decided by [nextDocumentScope], which the
  // dropdown menu sets immediately before launching the picker.
  val pickDocuments =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
      if (uris.isNotEmpty()) {
        val resolver = context.contentResolver
        val newDocs = uris.mapNotNull { uri ->
          // Persist read permission so we can still read on send.
          try {
            resolver.takePersistableUriPermission(
              uri,
              android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          } catch (_: Throwable) { /* picker URIs are read-allowed for session in any case */ }

          var displayName = uri.lastPathSegment ?: "document"
          var size = -1L
          try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
              if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
              }
            }
          } catch (t: Throwable) {
            Log.w(TAG, "Could not query metadata for $uri", t)
          }

          val ext = displayName.substringAfterLast('.', "").lowercase()
          val mime = resolver.getType(uri)
          val supported = ext in DocumentTextExtractor.SUPPORTED_EXTENSIONS ||
            mime?.startsWith("text/") == true ||
            mime == "application/json" ||
            mime == "application/xml" ||
            mime == "application/rtf" ||
            mime == "application/pdf"
          if (!supported) {
            Log.w(TAG, "Rejected unsupported document: $displayName ($mime)")
            return@mapNotNull null
          }
          DocumentAttachment(
            uri = uri,
            displayName = displayName,
            sizeBytes = size,
            mimeType = mime,
            scope = nextDocumentScope,
          )
        }
        if (newDocs.isNotEmpty()) {
          pickedDocuments = pickedDocuments + newDocs
          // Kick off extraction + chunking (and ingestion for persistent docs) in the
          // background right away, so when the user finally hits "send" the heavy work is
          // already done and the model can start generating almost immediately.
          modelManagerViewModel.chatAttachments.preIngest(newDocs)
        }
      }
    }

  // Stage attached documents + memory flag into the RAG coordinator and clear local UI
  // state. Returns the snapshot of attached docs (as message-side descriptors) so the
  // caller can pass them straight into [createMessagesToSend] — keeping the document
  // chips visible *inside the chat bubble* while indexing finishes in the background.
  val stageRagAndClear: () -> List<AttachedDocumentInfo> = {
    val snapshot = pickedDocuments.map {
      AttachedDocumentInfo(
        uriKey = it.uri.toString(),
        displayName = it.displayName,
        isPersistent = it.scope == AttachmentScope.PERSISTENT,
      )
    }
    modelManagerViewModel.chatAttachments.stage(
      conversationKey = modelManagerUiState.selectedModel.name,
      staging = PendingRagStaging(
        documents = pickedDocuments,
      ),
    )
    pickedDocuments = listOf()
    snapshot
  }

  DisposableEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.addObserver(sensorObserver)
    onDispose { lifecycleOwner.lifecycle.removeObserver(sensorObserver) }
  }

  // Listen for "el usuario tocó un documento permanente en el panel" events emitted por el
  // coordinator y añadirlos a `pickedDocuments` como si los hubiera elegido vía el menú de
  // adjuntos. El coordinator ya precarga el texto y marca el indexado como READY.
  LaunchedEffect(Unit) {
    modelManagerViewModel.chatAttachments.permanentAttachEvents.collect { doc ->
      if (pickedDocuments.none { it.uri == doc.uri }) {
        pickedDocuments = pickedDocuments + doc
      }
    }
  }

  Column {
    // A preview panel for the selected images and audio clips.
    if (pickedImages.isNotEmpty() || pickedAudioClips.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Spacer(modifier = Modifier.width(16.dp))

        for (image in pickedImages) {
          Box(contentAlignment = Alignment.TopEnd) {
            Image(
              bitmap = image.asImageBitmap(),
              contentDescription = stringResource(R.string.cd_image_thumbnail),
              modifier =
                Modifier.height(80.dp)
                  .shadow(2.dp, shape = RoundedCornerShape(8.dp))
                  .clip(RoundedCornerShape(8.dp))
                  .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
            MediaPanelCloseButton { pickedImages = pickedImages.filter { image != it } }
          }
        }

        for ((index, audioClip) in pickedAudioClips.withIndex()) {
          Box(contentAlignment = Alignment.TopEnd) {
            Box(
              modifier =
                Modifier.shadow(2.dp, shape = RoundedCornerShape(8.dp))
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.surface)
                  .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
              AudioPlaybackPanel(
                audioData = audioClip.audioData,
                sampleRate = audioClip.sampleRate,
                isRecording = false,
                modifier = Modifier.padding(end = 16.dp),
              )
            }
            MediaPanelCloseButton {
              pickedAudioClips = pickedAudioClips.filterIndexed { curIndex, curAudioData ->
                curIndex != index
              }
            }
          }
        }

        Spacer(modifier = Modifier.width(16.dp))
      }
    }

    // A second preview row dedicated to document attachments. Reusamos el mismo
    // `MessageDocumentChip` que se ve dentro de las burbujas para mantener el feedback
    // visual coherente: mini-progreso circular durante el procesado y check verde
    // brillante cuando el documento está listo (READY).
    if (pickedDocuments.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Spacer(modifier = Modifier.width(16.dp))

        androidx.compose.runtime.CompositionLocalProvider(
          com.google.ai.edge.gallery.ui.common.chat.rag.LocalChatAttachments
            provides modelManagerViewModel.chatAttachments,
        ) {
          for ((index, doc) in pickedDocuments.withIndex()) {
            Box(contentAlignment = Alignment.TopEnd) {
              MessageDocumentChip(
                doc = AttachedDocumentInfo(
                  uriKey = doc.uri.toString(),
                  displayName = doc.displayName,
                  isPersistent = doc.scope == AttachmentScope.PERSISTENT,
                ),
              )
              MediaPanelCloseButton {
                modelManagerViewModel.chatAttachments.forget(doc.uri.toString())
                pickedDocuments = pickedDocuments.filterIndexed { i, _ -> i != index }
              }
            }
          }
        }

        Spacer(modifier = Modifier.width(16.dp))
      }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 76.dp)) {
            Column(
              modifier =
                Modifier.padding(horizontal = 12.dp)
                  .padding(vertical = 8.dp)
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
              // First row: text field for input.
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Text field.
                val cdPromptInput = stringResource(R.string.cd_prompt_input_text_field)
                TextField(
                  value = curMessage,
                  minLines = 1,
                  maxLines = 3,
                  onValueChange = onValueChanged,
                  colors =
                    TextFieldDefaults.colors(
                      unfocusedContainerColor = Color.Transparent,
                      focusedContainerColor = Color.Transparent,
                      focusedIndicatorColor = Color.Transparent,
                      unfocusedIndicatorColor = Color.Transparent,
                      disabledIndicatorColor = Color.Transparent,
                      disabledContainerColor = Color.Transparent,
                    ),
                  textStyle = bodyLargeNarrow,
                  modifier = Modifier.weight(1f).semantics { contentDescription = cdPromptInput },
                  placeholder = { Text(stringResource(textFieldPlaceHolderRes)) },
                )
                Spacer(modifier = Modifier.width(4.dp))
              }

              // Second row: buttons to add extra content, and the action button.
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).offset(y = (-8).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                // Left side: add content button.
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  // A plus button to show a popup menu to add stuff to the chat.
                  Box() {
                    val enableAddButton = !inProgress && !isResettingSession && !modelInitializing
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                          if (enableAddButton) MaterialTheme.colorScheme.surfaceVariant
                          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                        .then(
                          if (enableAddButton) Modifier.clickable { showAddContentMenu = true }
                          else Modifier
                        ),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.cd_add_content_icon),
                        tint = if (enableAddButton) MaterialTheme.colorScheme.onSurfaceVariant
                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp),
                      )
                    }

                    DropdownMenu(
                      expanded = showAddContentMenu,
                      onDismissRequest = { showAddContentMenu = false },
                      shape = RoundedCornerShape(16.dp),
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                      if (showImagePicker) {
                        val isImageLimitExceededForAiCore =
                          modelManagerUiState.selectedModel.runtimeType == RuntimeType.AICORE &&
                            (imageCount + pickedImages.size) >= MAX_IMAGE_COUNT_AI_CORE
                        val enableAddImageMenuItems =
                          (imageCount + pickedImages.size) < MAX_IMAGE_COUNT
                        // Take a picture.
                        DropdownMenuItem(
                          text = {
                            Row(
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                              Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                              Text("Take a picture")
                            }
                          },
                          enabled = enableAddImageMenuItems,
                          onClick = {
                            if (isImageLimitExceededForAiCore) {
                              onImageLimitExceeded()
                              showAddContentMenu = false
                              return@DropdownMenuItem
                            }
                            // Check permission
                            when (PackageManager.PERMISSION_GRANTED) {
                              // Already got permission. Call the lambda.
                              ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                              ) -> {
                                showAddContentMenu = false
                                showCameraCaptureBottomSheet = true
                              }

                              // Otherwise, ask for permission
                              else -> {
                                takePicturePermissionLauncher.launch(Manifest.permission.CAMERA)
                              }
                            }
                          },
                        )

                        // Pick an image from album.
                        DropdownMenuItem(
                          text = {
                            Row(
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                              Icon(Icons.Rounded.Photo, contentDescription = null)
                              Text("Pick from album")
                            }
                          },
                          enabled = enableAddImageMenuItems,
                          onClick = {
                            if (isImageLimitExceededForAiCore) {
                              onImageLimitExceeded()
                              showAddContentMenu = false
                              return@DropdownMenuItem
                            }
                            // Launch the photo picker and let the user choose only images.
                            pickMedia.launch(
                              PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                              )
                            )
                            showAddContentMenu = false
                          },
                        )
                      }

                      // Audio menu items removed - mic button is now permanently visible next to send.

                      // Attach a TEMPORARY document (ephemeral RAG for this request only).
                      DropdownMenuItem(
                        text = {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                          ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null)
                            Text("Adjuntar documento temporal")
                          }
                        },
                        onClick = {
                          showAddContentMenu = false
                          nextDocumentScope = AttachmentScope.TEMPORARY
                          pickDocuments.launch(DocumentTextExtractor.PICKER_MIME_TYPES)
                        },
                      )

                      // Attach a PERSISTENT document (goes into the on-disk RAG).
                      DropdownMenuItem(
                        text = {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                          ) {
                            Icon(Icons.Outlined.Description, contentDescription = null)
                            Text("Adjuntar documento permanente")
                          }
                        },
                        onClick = {
                          showAddContentMenu = false
                          nextDocumentScope = AttachmentScope.PERSISTENT
                          pickDocuments.launch(DocumentTextExtractor.PICKER_MIME_TYPES)
                        },
                      )


                      // Prompt history.
                      DropdownMenuItem(
                        text = {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                          ) {
                            Icon(Icons.Rounded.History, contentDescription = null)
                            Text("Input history")
                          }
                        },
                        onClick = {
                          showAddContentMenu = false
                          showTextInputHistorySheet = true
                        },
                      )
                    }
                  }

                  // Skills removed from here - now in top bar.

                }

                // Right side: action buttons with uniform size and minimal spacing.
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                // Audio record button: hold to send, tap to toggle context recording.
                // State is declared at the outer scope so it is properly disposed
                // and can be safely stopped when switching to other modes.
                val holdEnabled = !inProgress && !isResettingSession && !modelInitializing

                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                      if (isHoldRecording || isContextRecording) MaterialTheme.colorScheme.error
                      else if (holdEnabled) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                    .then(
                      if (holdEnabled) {
                        // Key only on holdEnabled so toggling isContextRecording from
                        // within onPress does NOT recompose / restart the gesture
                        // detector mid-press (which would lose the next tap).
                        Modifier.pointerInput(holdEnabled) {
                          detectTapGestures(
                            onPress = {
                              // Check permission first
                              if (ContextCompat.checkSelfPermission(
                                  context, Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                              ) {
                                recordAudioClipsPermissionLauncher.launch(
                                  Manifest.permission.RECORD_AUDIO
                                )
                                return@detectTapGestures
                              }

                              // If context recording is active, stop it on any press
                              // and attach the recorded clip as an audio attachment.
                              if (isContextRecording) {
                                val audioData = stopHoldToTalkRecording(contextAudioRecordState, contextAudioStream)
                                isContextRecording = false
                                // Defensive: also tear down any leftover hold-mode
                                // recorder/buffer to keep both modes independent.
                                stopHoldToTalkRecording(holdAudioRecordState, holdAudioStream)
                                holdAudioStream.reset()
                                onSetAudioRecorderVisible(false)
                                if (audioData.isNotEmpty()) {
                                  val audioClip = AudioClip(audioData = audioData, sampleRate = SAMPLE_RATE)
                                  updatePickedAudioClips(listOf(audioClip))
                                }
                                return@detectTapGestures
                              }

                              // Defensive: before starting a fresh hold/toggle
                              // recording, ensure both buffers/recorders are
                              // reset so a previous mode's leftovers don't
                              // shadow the new session.
                              stopHoldToTalkRecording(holdAudioRecordState, holdAudioStream)
                              stopHoldToTalkRecording(contextAudioRecordState, contextAudioStream)
                              holdAudioStream.reset()
                              contextAudioStream.reset()

                              // Start recording. CRITICAL: create + start the
                              // AudioRecord SYNCHRONOUSLY here so that when the
                              // user releases (even instantly), stopHoldToTalkRecording
                              // is guaranteed to find the recorder. Only the read
                              // loop runs in IO. Previously both creation and loop
                              // ran in IO, causing a race where a quick release
                              // produced an empty buffer and left a zombie recorder
                              // holding the mic — which then prevented context-mode
                              // recording from capturing anything.
                              isHoldRecording = true
                              onSetAudioRecorderVisible(true)
                              val pressStartMs = System.currentTimeMillis()
                              val recorder = try {
                                createAndStartHoldRecorder(holdAudioRecordState, holdAudioStream)
                              } catch (t: Throwable) {
                                android.util.Log.e("MessageInputText", "Failed to start hold recorder", t)
                                isHoldRecording = false
                                onSetAudioRecorderVisible(false)
                                return@detectTapGestures
                              }
                              val recordJob = scope.launch(Dispatchers.IO) {
                                runHoldRecordingLoop(
                                  recorder, holdAudioStream,
                                  onAmplitudeChanged = onAmplitudeChanged,
                                )
                              }
                              // Wait for release
                              val released = tryAwaitRelease()
                              isHoldRecording = false

                              if (!released) {
                                // Cancelled - stop and discard
                                stopHoldToTalkRecording(holdAudioRecordState, holdAudioStream)
                                recordJob.cancel()
                                onSetAudioRecorderVisible(false)
                                return@detectTapGestures
                              }

                              val pressDurationMs = System.currentTimeMillis() - pressStartMs
                              val audioData = stopHoldToTalkRecording(holdAudioRecordState, holdAudioStream)
                              recordJob.cancel()
                              // Use ELAPSED TIME to discriminate short tap vs hold,
                              // not the captured byte count. Byte count was unreliable
                              // because a short tap can race ahead of the IO loop and
                              // produce 0 bytes, yielding a false "short tap".
                              val SHORT_TAP_MS = 300L
                              if (pressDurationMs < SHORT_TAP_MS) {
                                // Short tap: switch to context (toggle) recording mode.
                                contextAudioStream.reset()
                                isContextRecording = true
                                // Keep audio recorder visible for amplitude animation.
                                // Same synchronous-create pattern as above.
                                val ctxRecorder = try {
                                  createAndStartHoldRecorder(contextAudioRecordState, contextAudioStream)
                                } catch (t: Throwable) {
                                  android.util.Log.e("MessageInputText", "Failed to start context recorder", t)
                                  isContextRecording = false
                                  onSetAudioRecorderVisible(false)
                                  return@detectTapGestures
                                }
                                scope.launch(Dispatchers.IO) {
                                  runHoldRecordingLoop(
                                    ctxRecorder, contextAudioStream,
                                    onAmplitudeChanged = onAmplitudeChanged,
                                  )
                                }
                              } else {
                                // Long hold: send audio combined with current text and images
                                onSetAudioRecorderVisible(false)
                                if (audioData.isNotEmpty()) {
                                  val audioClip = com.google.ai.edge.gallery.common.AudioClip(audioData = audioData, sampleRate = SAMPLE_RATE)
                                  val combinedAudioClips = pickedAudioClips + listOf(audioClip)
                                  val docsSnapshot = stageRagAndClear()
                                  onSendMessage(
                                    createMessagesToSend(
                                      pickedImages = pickedImages,
                                      audioClips = combinedAudioClips,
                                      text = curMessage.trim(),
                                      documents = docsSnapshot,
                                    )
                                  )
                                  pickedImages = listOf()
                                  pickedAudioClips = listOf()
                                }
                              }
                            }
                          )
                        }
                      } else Modifier
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    if (isHoldRecording) Icons.Rounded.Stop
                    else if (isContextRecording) Icons.Rounded.Stop
                    else Icons.Rounded.Mic,
                    contentDescription = if (isHoldRecording) "Recording..." else if (isContextRecording) "Stop recording" else "Hold to send, tap to record",
                    tint = if (isHoldRecording || isContextRecording) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                  )
                }

                // Conversation mode button.
                val convEnabled = !inProgress && !isResettingSession && !modelInitializing
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                      if (convEnabled) MaterialTheme.colorScheme.tertiaryContainer
                      else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                    .then(if (convEnabled) Modifier.clickable {
                      // Ensure the hold/tap recorder is fully released before
                      // the conversation mode's own AudioRecord tries to claim
                      // the microphone. This makes both modes independent.
                      stopAnyActiveRecording()
                      onConversationModeClicked()
                    } else Modifier),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = "Conversation mode",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                  )
                }

                // Stop button.
                if (inProgress && showStopButtonWhenInProgress) {
                  // Show the stop button for the entire processing window
                  // (including the prefill / "preparing" phase) so the user
                  // can always cancel an in-flight prompt.
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.secondaryContainer)
                      .clickable { onStopButtonClicked() },
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      Icons.Rounded.Stop,
                      contentDescription = stringResource(R.string.cd_stop_icon),
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
                // Send button.
                else {
                  val sendEnabled = !inProgress && !isResettingSession &&
                    (curMessage.isNotEmpty() || pickedAudioClips.isNotEmpty() || pickedImages.isNotEmpty())
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(
                        if (sendEnabled) getTaskIconColor(task = task)
                        else getTaskIconColor(task = task).copy(alpha = 0.3f)
                      )
                      .then(if (sendEnabled) Modifier.clickable {
                        try {
                          var message = curMessage.trim()
                          val docsSnapshot = stageRagAndClear()
                          android.util.Log.i(
                            "BlueEdgePerf",
                            "MIT.send onClick chars=${message.length} images=${pickedImages.size} audio=${pickedAudioClips.size} docs=${docsSnapshot.size}",
                          )
                          val msgs = createMessagesToSend(
                            pickedImages = pickedImages,
                            audioClips = pickedAudioClips,
                            text = message,
                            documents = docsSnapshot,
                          )
                          android.util.Log.i(
                            "BlueEdgePerf",
                            "MIT.send -> messages=${msgs.size} types=${msgs.map { it.type }}",
                          )
                          onSendMessage(msgs)
                          pickedImages = listOf()
                          pickedAudioClips = listOf()
                        } catch (t: Throwable) {
                          android.util.Log.e("BlueEdgeCrash", "MIT.send click crashed", t)
                          throw t
                        }
                      } else Modifier),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      Icons.AutoMirrored.Rounded.Send,
                      contentDescription = stringResource(R.string.cd_send_prompt_icon),
                      modifier = Modifier.offset(x = 2.dp).size(20.dp),
                      tint = Color.White,
                    )
                  }
                }
                } // End right-side action buttons Row
              }
            }
    }
  }

  // A bottom sheet to show the text input history to pick from.
  if (showTextInputHistorySheet) {
    TextInputHistorySheet(
      history = modelManagerUiState.textInputHistory,
      onDismissed = { showTextInputHistorySheet = false },
      onHistoryItemClicked = { item ->
        val docsSnapshot = stageRagAndClear()
        onSendMessage(
          createMessagesToSend(
            pickedImages = pickedImages,
            audioClips = pickedAudioClips,
            text = item,
            documents = docsSnapshot,
          )
        )
        pickedImages = listOf()
        pickedAudioClips = listOf()
        modelManagerViewModel.promoteTextInputHistoryItem(item)
      },
      onHistoryItemDeleted = { item -> modelManagerViewModel.deleteTextInputHistory(item) },
      onHistoryItemsDeleteAll = { modelManagerViewModel.clearTextInputHistory() },
    )
  }

  if (showCameraCaptureBottomSheet) {
    ModalBottomSheet(
      sheetState = cameraCaptureSheetState,
      onDismissRequest = { showCameraCaptureBottomSheet = false },
    ) {
      val lifecycleOwner = LocalLifecycleOwner.current
      val previewUseCase = remember { androidx.camera.core.Preview.Builder().build() }
      val imageCaptureUseCase = remember {
        // Try to limit the image size.
        val preferredSize = Size(512, 512)
        val resolutionStrategy =
          ResolutionStrategy(
            preferredSize,
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
          )
        val resolutionSelector =
          ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
      }
      var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
      var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
      val localContext = LocalContext.current
      var cameraSide by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
      val executor = remember { Executors.newSingleThreadExecutor() }

      fun rebindCameraProvider() {
        cameraProvider?.let { cameraProvider ->
          val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraSide).build()
          try {
            cameraProvider.unbindAll()
            val camera =
              cameraProvider.bindToLifecycle(
                lifecycleOwner = lifecycleOwner,
                cameraSelector = cameraSelector,
                previewUseCase,
                imageCaptureUseCase,
              )
            cameraControl = camera.cameraControl
          } catch (e: Exception) {
            Log.d(TAG, "Failed to bind camera", e)
          }
        }
      }

      LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.awaitInstance(localContext)
        rebindCameraProvider()
      }

      LaunchedEffect(cameraSide) { rebindCameraProvider() }

      DisposableEffect(Unit) { // Or key on lifecycleOwner if it makes more sense
        onDispose {
          cameraProvider?.unbindAll() // Unbind all use cases from the camera provider
          if (!executor.isShutdown) {
            executor.shutdown() // Shut down the executor service
          }
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        // PreviewView for the camera feed.
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            PreviewView(ctx).also {
              previewUseCase.surfaceProvider = it.surfaceProvider
              rebindCameraProvider()
            }
          },
        )

        // Close button.
        IconButton(
          onClick = {
            scope.launch {
              cameraCaptureSheetState.hide()
              showCameraCaptureBottomSheet = false
            }
          },
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
          modifier = Modifier.offset(x = (-8).dp, y = 8.dp).align(Alignment.TopEnd),
        ) {
          Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_close_icon),
            tint = MaterialTheme.colorScheme.primary,
          )
        }

        // Button that triggers the image capture process
        IconButton(
          colors =
            IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = 32.dp)
              .size(size = 64.dp)
              .border(width = 2.dp, color = MaterialTheme.colorScheme.onPrimary, CircleShape),
          onClick = {
            val callback =
              object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                  try {
                    var bitmap = image.toBitmap()
                    val rotation = sensorObserver.currentRotation + image.imageInfo.rotationDegrees
                    bitmap =
                      if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                      } else bitmap
                    bitmap = resizeBitmap(originalBitmap = bitmap)
                    updatePickedImages(listOf(bitmap))
                  } catch (e: Exception) {
                    Log.e(TAG, "Failed to process image", e)
                  } finally {
                    image.close()
                    scope.launch {
                      cameraCaptureSheetState.hide()
                      showCameraCaptureBottomSheet = false
                    }
                  }
                }
              }
            imageCaptureUseCase.takePicture(executor, callback)
          },
        ) {
          Icon(
            Icons.Rounded.PhotoCamera,
            contentDescription = stringResource(R.string.cd_camera_shutter_icon),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp),
          )
        }

        // Button that toggles the front and back camera.
        if (hasFrontCamera) {
          IconButton(
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            modifier =
              Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp, end = 32.dp).size(48.dp),
            onClick = {
              cameraSide =
                when (cameraSide) {
                  CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
                  else -> CameraSelector.LENS_FACING_BACK
                }
            },
          ) {
            Icon(
              Icons.Rounded.FlipCameraAndroid,
              contentDescription = stringResource(R.string.cd_toggle_front_back_camera_icon),
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MediaPanelCloseButton(onClicked: () -> Unit) {
  Box(
    modifier =
      Modifier.offset(x = 10.dp, y = (-10).dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
        .border((1.5).dp, MaterialTheme.colorScheme.outline, CircleShape)
        .clickable { onClicked() }
  ) {
    Icon(
      Icons.Rounded.Close,
      contentDescription = stringResource(R.string.cd_delete_icon),
      modifier = Modifier.padding(3.dp).size(16.dp),
    )
  }
}

private fun handleImagesSelected(
  context: Context,
  uris: List<Uri>,
  onImagesSelected: (List<Bitmap>) -> Unit,
) {
  val images: MutableList<Bitmap> = mutableListOf()
  for (uri in uris) {
    val bitmap: Bitmap? =
      try {
        val inputStream =
          if (uri.scheme == null || uri.scheme == "file") {
            FileInputStream(uri.path ?: "")
          } else {
            context.contentResolver.openInputStream(uri)
          }
        if (inputStream != null) {
          // Read the EXIF metadata from the picture and rotate it correctly.
          val exif = ExifInterface(inputStream)
          val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          // You MUST close the first input stream before opening another one on the same URI.
          inputStream.close()

          // The let block will now return the rotated bitmap
          decodeSampledBitmapFromUri(context, uri, 1024, 1024)?.let { originalBitmap ->
            rotateBitmap(bitmap = originalBitmap, orientation = orientation)
          }
        } else {
          null
        }
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    if (bitmap != null) {
      images.add(bitmap)
    }
  }
  if (images.isNotEmpty()) {
    onImagesSelected(images)
  }
}

private fun handleAudioWavSelected(
  context: Context,
  uri: Uri,
  onAudioSelected: (AudioClip) -> Unit,
) {
  convertWavToMonoWithMaxSeconds(context = context, stereoUri = uri)?.let { audioClip ->
    onAudioSelected(audioClip)
  }
}

/**
 * Resizes a given Bitmap to fit within a square of a specified size, while maintaining its original
 * aspect ratio.
 */
private fun resizeBitmap(originalBitmap: Bitmap, size: Int = 1024): Bitmap {
  val originalWidth = originalBitmap.width
  val originalHeight = originalBitmap.height

  // Return the original bitmap if it's already within the specified size.
  if (originalWidth <= size && originalHeight <= size) {
    return originalBitmap
  }

  val aspectRatio: Float = originalWidth.toFloat() / originalHeight.toFloat()
  val newWidth: Int
  val newHeight: Int

  if (aspectRatio > 1) {
    // Landscape or square orientation
    newWidth = size
    newHeight = (size / aspectRatio).toInt()
  } else {
    // Portrait orientation
    newHeight = size
    newWidth = (size * aspectRatio).toInt()
  }

  Log.d(TAG, "Resizing image from $originalWidth x $originalHeight to $newWidth x $newHeight")

  // Create a new scaled bitmap using the calculated dimensions
  return originalBitmap.scale(newWidth, newHeight)
}

private fun checkFrontCamera(context: Context, callback: (Boolean) -> Unit) {
  val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
  cameraProviderFuture.addListener(
    {
      val cameraProvider = cameraProviderFuture.get()
      try {
        // Attempt to select the default front camera
        val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        callback(hasFront)
      } catch (e: Exception) {
        e.printStackTrace()
        callback(false)
      }
    },
    ContextCompat.getMainExecutor(context),
  )
}

private fun createMessagesToSend(
  pickedImages: List<Bitmap>,
  audioClips: List<AudioClip>,
  text: String,
  documents: List<AttachedDocumentInfo> = listOf(),
): List<ChatMessage> {
  val messages: MutableList<ChatMessage> = mutableListOf()

  // Cap images.
  var curPickedImages = pickedImages.toList()
  if (curPickedImages.size > MAX_IMAGE_COUNT) {
    curPickedImages = curPickedImages.subList(fromIndex = 0, toIndex = MAX_IMAGE_COUNT)
  }

  // Build audio clip messages.
  var audioMessages: MutableList<ChatMessageAudioClip> = mutableListOf()
  if (audioClips.isNotEmpty()) {
    for (audioClip in audioClips) {
      audioMessages.add(
        ChatMessageAudioClip(
          audioData = audioClip.audioData,
          sampleRate = audioClip.sampleRate,
          side = ChatSide.USER,
        )
      )
    }
  }
  if (audioMessages.size > MAX_AUDIO_CLIP_COUNT) {
    audioMessages = audioMessages.subList(fromIndex = 0, toIndex = MAX_AUDIO_CLIP_COUNT)
  }

  // If there are multiple content types — OR any document attachments at all — combine
  // into a single composite message so the chat bubble can render text + media + the
  // document chips (with their own progress wheel) side by side.
  val hasImages = curPickedImages.isNotEmpty()
  val hasAudio = audioMessages.isNotEmpty()
  val hasText = text.isNotEmpty()
  val hasDocs = documents.isNotEmpty()
  val contentCount = listOf(hasImages, hasAudio, hasText).count { it }

  if (contentCount > 1 || hasDocs) {
    messages.add(
      ChatMessageComposite(
        content = text,
        bitmaps = curPickedImages,
        imageBitMaps = curPickedImages.map { it.asImageBitmap() },
        audioClips = audioMessages,
        documents = documents,
        side = ChatSide.USER,
      )
    )
  } else {
    // Single content type: keep original behavior.
    if (hasImages) {
      messages.add(
        ChatMessageImage(
          bitmaps = curPickedImages,
          imageBitMaps = curPickedImages.map { it.asImageBitmap() },
          side = ChatSide.USER,
        )
      )
    }
    messages.addAll(audioMessages)
    if (hasText) {
      messages.add(ChatMessageText(content = text, side = ChatSide.USER))
    }
  }

  return messages
}

/**
 * A private class that acts as a LifecycleObserver to monitor sensor events for a device's
 * orientation, specifically using the accelerometer.
 *
 * This observer registers for accelerometer events in `onResume` and unregisters in `onPause` to
 * conserve battery and resources. It calculates the device's rotation (0, 90, 180, -90) by checking
 * if the acceleration on the X or Y axis exceeds a threshold of 7.0 m/s^2, which corresponds to
 * gravity's pull when the device is held in a cardinal direction. A 'dead zone' is used to prevent
 * the rotation from "chattering" when the device is held at an angle between the cardinal
 * directions.
 */
private class SensorObserver(context: Context) : DefaultLifecycleObserver, SensorEventListener {
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  var currentRotation = 0

  override fun onResume(owner: LifecycleOwner) {
    super.onResume(owner)
    accelerometer?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    super.onPause(owner)
    sensorManager.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
      val x = event.values[0]
      val y = event.values[1]

      // When the phone is on its side, gravity acts primarily along the x-axis.
      // When the phone is upright, gravity acts primarily along the y-axis.
      val newOrientation =
        when {
          x < -7.0 -> 90
          x > 7.0 -> -90
          y < -7.0 -> 180
          y > 7.0 -> 0
          else -> currentRotation // Keep the last known orientation
        }

      if (newOrientation != currentRotation) {
        currentRotation = newOrientation
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

private const val HOLD_SAMPLE_RATE = 16000
private const val HOLD_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val HOLD_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

@SuppressLint("MissingPermission")
private fun createAndStartHoldRecorder(
  audioRecordState: androidx.compose.runtime.MutableState<AudioRecord?>,
  audioStream: java.io.ByteArrayOutputStream,
): AudioRecord {
  val minBufferSize = AudioRecord.getMinBufferSize(HOLD_SAMPLE_RATE, HOLD_CHANNEL_CONFIG, HOLD_AUDIO_FORMAT)
  // Tear down any leftover recorder synchronously before grabbing the mic again,
  // so we never end up with two AudioRecord instances fighting for it.
  audioRecordState.value?.let { old ->
    try {
      if (old.recordingState == AudioRecord.RECORDSTATE_RECORDING) old.stop()
    } catch (_: Throwable) {}
    try { old.release() } catch (_: Throwable) {}
  }
  val recorder = AudioRecord(
    MediaRecorder.AudioSource.MIC, HOLD_SAMPLE_RATE, HOLD_CHANNEL_CONFIG, HOLD_AUDIO_FORMAT, minBufferSize,
  )
  audioStream.reset()
  recorder.startRecording()
  // Publish the started recorder ONLY after startRecording() succeeded so that
  // a concurrent stop() call always sees a recorder that is actually running.
  audioRecordState.value = recorder
  return recorder
}

/** Read loop. Must be invoked from a background thread (e.g. Dispatchers.IO). */
private fun runHoldRecordingLoop(
  recorder: AudioRecord,
  audioStream: java.io.ByteArrayOutputStream,
  onAmplitudeChanged: (Int) -> Unit,
) {
  val minBufferSize = AudioRecord.getMinBufferSize(HOLD_SAMPLE_RATE, HOLD_CHANNEL_CONFIG, HOLD_AUDIO_FORMAT)
  val buffer = ByteArray(minBufferSize)
  while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
    val bytesRead = try {
      recorder.read(buffer, 0, buffer.size)
    } catch (_: Throwable) { -1 }
    if (bytesRead > 0) {
      audioStream.write(buffer, 0, bytesRead)
      // Calculate amplitude for visual feedback.
      var maxAmplitude = 0
      for (i in 0 until bytesRead step 2) {
        if (i + 1 < bytesRead) {
          val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
          val absVal = kotlin.math.abs(sample.toShort().toInt())
          if (absVal > maxAmplitude) maxAmplitude = absVal
        }
      }
      onAmplitudeChanged(maxAmplitude)
    } else if (bytesRead < 0) {
      break
    }
  }
}

private fun stopHoldToTalkRecording(
  audioRecordState: androidx.compose.runtime.MutableState<AudioRecord?>,
  audioStream: java.io.ByteArrayOutputStream,
): ByteArray {
  audioRecordState.value?.let { recorder ->
    try {
      if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        recorder.stop()
      }
    } catch (_: Throwable) {}
    try { recorder.release() } catch (_: Throwable) {}
  }
  audioRecordState.value = null
  val bytes = audioStream.toByteArray()
  // Reset so a subsequent recording session starts from an empty buffer even
  // if startHoldToTalkRecording isn't immediately invoked.
  audioStream.reset()
  return bytes
}
