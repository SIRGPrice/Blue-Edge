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

package com.google.ai.edge.gallery.ui.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskTopBarAction
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.isLegacyTasks
import com.google.ai.edge.gallery.ui.benchmark.BenchmarkScreen
import com.google.ai.edge.gallery.ui.common.ErrorDialog
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.chat.ModelDownloadStatusInfoPanel
import com.google.ai.edge.gallery.ui.modelmanager.GlobalModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGGalleryNavGraph"
private const val ROUTE_MODEL_LIST = "model_list"
private const val ROUTE_MODEL = "route_model"
private const val ROUTE_BENCHMARK = "benchmark"
private const val ROUTE_MODEL_MANAGER = "model_manager"
private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100

private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

private fun enterTween(): FiniteAnimationSpec<IntOffset> {
  return tween(
    ENTER_ANIMATION_DURATION_MS,
    easing = ENTER_ANIMATION_EASING,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
  )
}

private fun exitTween(): FiniteAnimationSpec<IntOffset> {
  return tween(EXIT_ANIMATION_DURATION_MS, easing = EXIT_ANIMATION_EASING)
}

private fun AnimatedContentTransitionScope<*>.slideEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
  )
}

private fun AnimatedContentTransitionScope<*>.slideExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
  )
}

private fun AnimatedContentTransitionScope<*>.slideUpEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Up,
  )
}

private fun AnimatedContentTransitionScope<*>.slideDownExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Down,
  )
}

/** Navigation routes. */
@Composable
fun GalleryNavHost(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var showModelManager by remember { mutableStateOf(false) }
  var pickedTask by remember { mutableStateOf<Task?>(null) }
  var enableHomeScreenAnimation by remember { mutableStateOf(true) }
  var enableModelListAnimation by remember { mutableStateOf(true) }
  var lastNavigatedModelName = remember { "" }

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerViewModel.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerViewModel.setAppInForeground(foreground = false)
        }
        else -> {
          /* Do nothing for other events */
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Auto-select the agent chat task on first composition.
  LaunchedEffect(Unit) {
    val agentChatTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_AGENT_CHAT)
    if (agentChatTask != null) {
      pickedTask = agentChatTask

      // Determine which model to load:
      // 1. Last loaded model (if still installed/downloaded)
      // 2. Any downloaded model
      // 3. First model in the list
      val downloadStatus = modelManagerViewModel.uiState.value.modelDownloadStatus
      val lastModelName = modelManagerViewModel.getLastLoadedModelName()
      val lastModel = lastModelName?.let { name ->
        agentChatTask.models.find { it.name == name &&
          downloadStatus[it.name]?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED }
      }
      val downloadedModel = lastModel ?: agentChatTask.models.firstOrNull {
        downloadStatus[it.name]?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED
      }
      val modelToLoad = downloadedModel ?: agentChatTask.models.firstOrNull()

      if (modelToLoad != null) {
        navController.navigate("$ROUTE_MODEL/${agentChatTask.id}/${modelToLoad.name}") {
          popUpTo(ROUTE_MODEL_LIST) { inclusive = true }
        }
      }
    }
  }

  NavHost(
    navController = navController,
    startDestination = ROUTE_MODEL_LIST,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
  ) {
    // Model list (now the landing screen).
    composable(
      route = ROUTE_MODEL_LIST,
    ) {
      pickedTask?.let {
        ModelManager(
          viewModel = modelManagerViewModel,
          task = it,
          enableAnimation = enableModelListAnimation,
          onModelClicked = { model ->
            navController.navigate("$ROUTE_MODEL/${it.id}/${model.name}")
          },
          navigateUp = {
            // No-op: model list is now the root screen
          },
        )
      }
    }

    // Model page.
    composable(
      route = "$ROUTE_MODEL/{taskId}/{modelName}",
      arguments =
        listOf(
          navArgument("taskId") { type = NavType.StringType },
          navArgument("modelName") { type = NavType.StringType },
        ),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""
      val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
      val scope = rememberCoroutineScope()
      val context = LocalContext.current

      modelManagerViewModel.getModelByName(name = modelName)?.let { initialModel ->
        if (lastNavigatedModelName != modelName) {
          modelManagerViewModel.selectModel(initialModel)
          lastNavigatedModelName = modelName
        }

        val customTask = modelManagerViewModel.getCustomTaskByTaskId(id = taskId)
        if (customTask != null) {
          if (isLegacyTasks(customTask.task.id)) {
            // Chat / legacy routes: register with the model lifecycle manager so the model is
            // kept loaded while we're here (and unloaded in background when no workflow needs it).
            val isAgentChat = customTask.task.id == BuiltInTaskId.LLM_AGENT_CHAT
            DisposableEffect(initialModel.name, isAgentChat) {
              if (isAgentChat) modelManagerViewModel.modelLifecycleManager.onChatEntered(initialModel)
              onDispose {
                if (isAgentChat) modelManagerViewModel.modelLifecycleManager.onChatLeft()
              }
            }
            // If a workflow is currently running AI nodes, show the pause dialog.
            val pauseRequested by modelManagerViewModel.modelLifecycleManager
              .pauseDialogRequested.collectAsState()
            if (isAgentChat && pauseRequested) {
              com.google.ai.edge.gallery.ui.common.ActiveWorkflowPauseDialog(
                onPause = {
                  modelManagerViewModel.modelLifecycleManager.cancelAllRunningAi?.invoke()
                  modelManagerViewModel.modelLifecycleManager.dismissPauseDialog()
                },
                onKeep = {
                  modelManagerViewModel.modelLifecycleManager.dismissPauseDialog()
                },
              )
            }
            customTask.MainScreen(
              data =
                CustomTaskDataForBuiltinTask(
                  modelManagerViewModel = modelManagerViewModel,
                  onNavUp = {
                    enableModelListAnimation = false
                    lastNavigatedModelName = ""
                    navController.navigateUp()
                  },
                  onTinyGardenClicked = {
                    // Navigate to Tiny Garden task with its first model
                    val tinyGardenTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_TINY_GARDEN)
                    if (tinyGardenTask != null) {
                      var firstModel = tinyGardenTask.models.firstOrNull()
                      // If no Tiny Garden model, add the currently selected agent chat model
                      if (firstModel == null) {
                        val selectedModel = modelManagerViewModel.uiState.value.selectedModel
                        tinyGardenTask.models.add(selectedModel)
                        firstModel = selectedModel
                      }
                      navController.navigate("$ROUTE_MODEL/${tinyGardenTask.id}/${firstModel.name}")
                    } else {
                      android.widget.Toast.makeText(context, "Tiny Garden task not available.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                  },
                  onAppSettingsClicked = {
                    android.widget.Toast.makeText(context, "App settings coming soon.", android.widget.Toast.LENGTH_SHORT).show()
                  },
                )
            )
          } else {
            var disableAppBarControls by remember { mutableStateOf(false) }
            var hideTopBar by remember { mutableStateOf(false) }
            var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
            var customLeadingAction by remember { mutableStateOf<CustomTaskTopBarAction?>(null) }
            CustomTaskScreen(
              task = customTask.task,
              modelManagerViewModel = modelManagerViewModel,
              customLeadingAction = customLeadingAction,
              onNavigateUp = {
                if (customNavigateUpCallback != null) {
                  customNavigateUpCallback?.invoke()
                } else {
                  enableModelListAnimation = false
                  lastNavigatedModelName = ""
                  navController.navigateUp()

                  // Do NOT unload the model here. The ModelLifecycleManager decides whether the
                  // model stays loaded (chat page will reuse it instantly, or a running workflow
                  // still needs it). It is only unloaded when the app is backgrounded AND no
                  // workflow references are alive.
                }
              },
              disableAppBarControls = disableAppBarControls,
              hideTopBar = hideTopBar,
              useThemeColor = customTask.task.useThemeColor,
            ) { bottomPadding ->
              customTask.MainScreen(
                data =
                  CustomTaskData(
                    modelManagerViewModel = modelManagerViewModel,
                    bottomPadding = bottomPadding,
                    setAppBarControlsDisabled = { disableAppBarControls = it },
                    setTopBarVisible = { hideTopBar = !it },
                    setCustomNavigateUpCallback = { customNavigateUpCallback = it },
                      setCustomLeadingAction = { customLeadingAction = it },
                      performNavigateUp = {
                        if (customNavigateUpCallback != null) {
                          customNavigateUpCallback?.invoke()
                        } else {
                          enableModelListAnimation = false
                          lastNavigatedModelName = ""
                          navController.navigateUp()
                        }
                      },
                  )
              )
            }
          }
        }
      }
    }

    // Global model manager page.
    composable(
      route = ROUTE_MODEL_MANAGER,
      enterTransition = {
        if (
          initialState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            initialState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideUpEnter()
        }
      },
      exitTransition = {
        if (
          targetState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            targetState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideDownExit()
        }
      },
    ) { backStackEntry ->
      GlobalModelManager(
        viewModel = modelManagerViewModel,
        navigateUp = {
          enableHomeScreenAnimation = false
          navController.navigateUp()
        },
        onModelSelected = { task, model ->
          navController.navigate("$ROUTE_MODEL/${task.id}/${model.name}")
        },
        onBenchmarkClicked = { model ->
          navController.navigate("$ROUTE_BENCHMARK/${model.name}")
        },
      )
    }

    // Benchmark creation page.
    composable(
      route = "$ROUTE_BENCHMARK/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""

      modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
        BenchmarkScreen(
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onBackClicked = {
            enableModelListAnimation = false
            navController.navigateUp()
          },
        )
      }
    }
  }

  // Handle incoming intents for deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  if (data != null) {
    intent.data = null
    Log.d(TAG, "navigation link clicked: $data")
    if (data.toString().startsWith("com.google.ai.edge.gallery://model/")) {
      if (data.pathSegments.size >= 2) {
        val taskId = data.pathSegments.get(data.pathSegments.size - 2)
        val modelName = data.pathSegments.last()
        modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
          navController.navigate("$ROUTE_MODEL/${taskId}/${model.name}")
        }
      } else {
        Log.e(TAG, "Malformed deep link URI received: $data")
      }
    } else if (data.toString() == "com.google.ai.edge.gallery://global_model_manager") {
      navController.navigate(ROUTE_MODEL_MANAGER)
    }
  }
}

@Composable
private fun CustomTaskScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  disableAppBarControls: Boolean,
  hideTopBar: Boolean,
  useThemeColor: Boolean,
  customLeadingAction: CustomTaskTopBarAction?,
  onNavigateUp: () -> Unit,
  content: @Composable (bottomPadding: Dp) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var navigatingUp by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }
  var appBarHeight by remember { mutableIntStateOf(0) }

  val handleNavigateUp = {
    // Restore main config values when leaving a task that has per-task configs.
    if (task.handleModelConfigChangesInTask) {
      // Save current task configs.
      selectedModel.configValuesByTaskId[task.id] = selectedModel.configValues
      // Restore main configs if stored.
      val mainConfigs = selectedModel.configValuesByTaskId["__main__"]
      if (mainConfigs != null) {
        selectedModel.configValues = mainConfigs
      }
    }
    navigatingUp = true
    onNavigateUp()
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  // Swap to per-task config values on first composition.
  LaunchedEffect(selectedModel.name, task.id) {
    if (task.handleModelConfigChangesInTask) {
      // Save main configs.
      if (!selectedModel.configValuesByTaskId.containsKey("__main__")) {
        selectedModel.configValuesByTaskId["__main__"] = selectedModel.configValues
      }
      // Restore task-specific configs if available.
      val taskConfigs = selectedModel.configValuesByTaskId[task.id]
      if (taskConfigs != null) {
        selectedModel.configValues = taskConfigs
      }
    }
  }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(
          TAG,
          "Initializing model '${selectedModel.name}' from CustomTaskScreen launched effect",
        )
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  // For StoCATstic the night-sky gradient is painted behind the entire Scaffold (including the
  // reserved space for the TopAppBar and the system navigation bar area) so the top bar appears
  // transparent over it and the app extends edge-to-edge under the nav buttons.
  val isStoCATstic = task.id == com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_TINY_GARDEN
  val stoCATsticBgModifier = if (isStoCATstic) {
    Modifier.background(
      Brush.verticalGradient(
        listOf(
          com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette.nightSky,
          com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette.deepSky,
        )
      )
    )
  } else Modifier

  Scaffold(
    modifier = stoCATsticBgModifier,
    containerColor = Color.Transparent,
    // StoCATstic draws its own background (gradient) under the system nav bar, so we disable the
    // Scaffold's automatic window insets to let the content (and background) extend edge-to-edge.
    contentWindowInsets = if (isStoCATstic) WindowInsets(0, 0, 0, 0)
    else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
    topBar = {
      AnimatedVisibility(
        !hideTopBar,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
      ) {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          inProgress = disableAppBarControls,
          modelPreparing = disableAppBarControls,
          canShowResetSessionButton = false,
          useThemeColor = useThemeColor,
          modifier =
            Modifier.onGloballyPositioned { coordinates -> appBarHeight = coordinates.size.height },
          hideModelSelector = task.models.size <= 1,
          customLeadingAction = customLeadingAction,
          onConfigChanged = { _, _ -> },
          onBackClicked = { handleNavigateUp() },
          onModelSelected = { prevModel, newSelectedModel ->
            val instanceToCleanUp = prevModel.instance
            scope.launch(Dispatchers.Default) {
              // Clean up prev model.
              if (prevModel.name != newSelectedModel.name) {
                modelManagerViewModel.cleanupModel(
                  context = context,
                  task = task,
                  model = prevModel,
                  instanceToCleanUp = instanceToCleanUp,
                )
              }

              // Update selected model.
              Log.d(TAG, "from model picker. new: ${newSelectedModel.name}")
              modelManagerViewModel.selectModel(model = newSelectedModel)
            }
          },
        )
      }
    }
  ) { innerPadding ->
    // Calculate the target height in Dp for the content's top padding.
    val targetPaddingDp =
      if (!hideTopBar && appBarHeight > 0) {
        // Convert measured pixel height to Dp
        with(LocalDensity.current) { appBarHeight.toDp() }
      } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      }

    // Animate the actual top padding value.
    val animatedTopPadding by
      animateDpAsState(
        targetValue = targetPaddingDp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TopPaddingAnimation",
      )

    Box(
      modifier =
        Modifier.padding(
          top = if (!hideTopBar) innerPadding.calculateTopPadding() else animatedTopPadding,
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
      AnimatedContent(
        targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      ) { targetState ->
        when (targetState) {
          // Main UI when model is downloaded.
          true -> content(innerPadding.calculateBottomPadding())
          // Model download
          false ->
            ModelDownloadStatusInfoPanel(
              model = selectedModel,
              task = task,
              modelManagerViewModel = modelManagerViewModel,
            )
        }
      }
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = {
        showErrorDialog = false
        onNavigateUp()
      },
    )
  }
}
