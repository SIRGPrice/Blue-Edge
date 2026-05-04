/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Shared Koin module wiring the cross-platform abstractions. `:app` and the
 * iOS host both call `startKoin { modules(sharedModules()) }` at launch.
 *
 * Keep this module *purely* in terms of `commonMain` types; platform actuals
 * are resolved through their `expect`/`actual` factories (already in place
 * for `LlmEngine`, `DownloadManager`, `OAuthClient`, `SecureStorage`,
 * `ModelStorage`, `Settings`).
 */
package com.blueedge.shared.di

import com.blueedge.shared.auth.OAuthClient
import com.blueedge.shared.auth.provideOAuthClient
import com.blueedge.shared.audio.AudioRecorder
import com.blueedge.shared.audio.AudioPlayer
import com.blueedge.shared.audio.provideAudioPlayer
import com.blueedge.shared.audio.provideAudioRecorder
import com.blueedge.shared.chat.ChatViewModel
import com.blueedge.shared.domain.ModelStorage
import com.blueedge.shared.domain.ModelImporter
import com.blueedge.shared.domain.provideModelImporter
import com.blueedge.shared.domain.provideModelStorage
import com.blueedge.shared.download.DownloadManager
import com.blueedge.shared.download.provideDownloadManager
import com.blueedge.shared.runtime.LlmEngine
import com.blueedge.shared.runtime.createLlmEngine
import com.blueedge.shared.storage.SecureStorage
import com.blueedge.shared.storage.SettingsRepository
import com.blueedge.shared.storage.provideSecureStorage
import com.blueedge.shared.storage.provideSettings
import com.blueedge.shared.ui.benchmark.BenchmarkRunner
import com.blueedge.shared.ui.benchmark.BenchmarkViewModel
import com.blueedge.shared.ui.home.HomeViewModel
import com.blueedge.shared.ui.modelmanager.ModelManagerViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedCoreModule: Module = module {
  single<LlmEngine> { createLlmEngine() }
  single<DownloadManager> { provideDownloadManager() }
  single<OAuthClient> { provideOAuthClient() }
  single<ModelStorage> { provideModelStorage() }
  single<ModelImporter> { provideModelImporter() }
  single<SecureStorage> { provideSecureStorage() }
  single<AudioRecorder> { provideAudioRecorder() }
  single<AudioPlayer> { provideAudioPlayer() }
  single { SettingsRepository(provideSettings()) }
  single { BenchmarkRunner(get()) }
  factory { ChatViewModel(settings = get()) }
  factory { BenchmarkViewModel(runner = get(), storage = get(), settings = get()) }
  factory { ModelManagerViewModel(storage = get(), engine = get(), settings = get(), importer = get()) }
  factory { HomeViewModel() }
}

fun sharedModules(): List<Module> = listOf(sharedCoreModule)


