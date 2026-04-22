/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic

import android.content.Context
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.BranchCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.DelayCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.FlashlightCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.GenericIntentCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.LlmDecisionCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.NotifyCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenAppCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenWifiSettingsCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SetVariableCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.TtsCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.VibrateCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ViewUriCapability
import com.google.ai.edge.gallery.customtasks.stocatstic.data.WorkflowRepository
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ActiveModelLlmRunner
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.LlmRunner
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.TriggerScheduler
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.WorkflowEngine
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.WorkflowRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Binds the StoCATstic task into the app-wide set and wires builtin capabilities. */
@Module
@InstallIn(SingletonComponent::class)
abstract class StocatsticTaskModule {
  @Binds @IntoSet abstract fun provideTask(impl: StocatsticTask): CustomTask

  @Binds @IntoSet abstract fun flashlight(c: FlashlightCapability): Capability
  @Binds @IntoSet abstract fun vibrate(c: VibrateCapability): Capability
  @Binds @IntoSet abstract fun tts(c: TtsCapability): Capability
  @Binds @IntoSet abstract fun notify(c: NotifyCapability): Capability
  @Binds @IntoSet abstract fun openApp(c: OpenAppCapability): Capability
  @Binds @IntoSet abstract fun viewUri(c: ViewUriCapability): Capability
  @Binds @IntoSet abstract fun genericIntent(c: GenericIntentCapability): Capability
  @Binds @IntoSet abstract fun wifi(c: OpenWifiSettingsCapability): Capability
  @Binds @IntoSet abstract fun delay(c: DelayCapability): Capability
  @Binds @IntoSet abstract fun branch(c: BranchCapability): Capability
  @Binds @IntoSet abstract fun setVar(c: SetVariableCapability): Capability
  @Binds @IntoSet abstract fun llm(c: LlmDecisionCapability): Capability

  // ----- Extra capabilities (gallery expansion) -------------------------------------------------
  @Binds @IntoSet abstract fun clipboardCopy(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ClipboardCopyCapability): Capability
  @Binds @IntoSet abstract fun clipboardRead(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ClipboardReadCapability): Capability
  @Binds @IntoSet abstract fun toast(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ToastCapability): Capability
  @Binds @IntoSet abstract fun setVolume(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SetVolumeCapability): Capability
  @Binds @IntoSet abstract fun ringerMode(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.RingerModeCapability): Capability
  @Binds @IntoSet abstract fun battery(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.BatteryReadCapability): Capability
  @Binds @IntoSet abstract fun bluetoothSettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenBluetoothSettingsCapability): Capability
  @Binds @IntoSet abstract fun locationSettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenLocationSettingsCapability): Capability
  @Binds @IntoSet abstract fun airplaneSettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenAirplaneModeSettingsCapability): Capability
  @Binds @IntoSet abstract fun soundSettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenSoundSettingsCapability): Capability
  @Binds @IntoSet abstract fun displaySettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenDisplaySettingsCapability): Capability
  @Binds @IntoSet abstract fun dataSettings(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.OpenDataUsageSettingsCapability): Capability
  @Binds @IntoSet abstract fun camera(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.LaunchCameraCapability): Capability
  @Binds @IntoSet abstract fun musicSearch(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.LaunchMusicSearchCapability): Capability
  @Binds @IntoSet abstract fun dial(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.DialCapability): Capability
  @Binds @IntoSet abstract fun sms(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SmsComposeCapability): Capability
  @Binds @IntoSet abstract fun email(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.EmailComposeCapability): Capability
  @Binds @IntoSet abstract fun share(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ShareTextCapability): Capability
  @Binds @IntoSet abstract fun maps(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.MapsSearchCapability): Capability
  @Binds @IntoSet abstract fun webSearch(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.WebSearchCapability): Capability
  @Binds @IntoSet abstract fun calendar(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.CalendarAddEventCapability): Capability
  @Binds @IntoSet abstract fun alarm(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SetAlarmCapability): Capability
  @Binds @IntoSet abstract fun timer(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SetTimerCapability): Capability
  @Binds @IntoSet abstract fun textFormat(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.TextFormatCapability): Capability
  @Binds @IntoSet abstract fun textTransform(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.TextTransformCapability): Capability
  @Binds @IntoSet abstract fun regexReplace(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.RegexReplaceCapability): Capability
  @Binds @IntoSet abstract fun math(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.MathCapability): Capability
  @Binds @IntoSet abstract fun compare(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.CompareCapability): Capability
  @Binds @IntoSet abstract fun randomNumber(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.RandomNumberCapability): Capability
  @Binds @IntoSet abstract fun randomChoice(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.RandomChoiceCapability): Capability
  @Binds @IntoSet abstract fun coin(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.CoinFlipCapability): Capability
  @Binds @IntoSet abstract fun currentTime(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.CurrentTimeCapability): Capability
  @Binds @IntoSet abstract fun weekday(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.WeekdayCapability): Capability
  @Binds @IntoSet abstract fun httpGet(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.HttpGetCapability): Capability
  @Binds @IntoSet abstract fun ping(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.PingCapability): Capability
  @Binds @IntoSet abstract fun logCap(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.LogCapability): Capability
  @Binds @IntoSet abstract fun counter(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.CounterIncrementCapability): Capability
  @Binds @IntoSet abstract fun passthrough(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.NoopCapability): Capability
  @Binds @IntoSet abstract fun failCap(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.FailCapability): Capability
  @Binds @IntoSet abstract fun successCap(c: com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.SuccessCapability): Capability

  @Binds abstract fun llmRunner(impl: ActiveModelLlmRunner): LlmRunner
}

/** Bootstraps [WorkflowRunner] singletons and re-syncs triggers at process start. */
@Module
@InstallIn(SingletonComponent::class)
object StocatsticBootstrapModule {
  @Provides @Singleton
  fun provideBootstrap(
    @ApplicationContext ctx: Context,
    engine: WorkflowEngine,
    repo: WorkflowRepository,
    scheduler: TriggerScheduler,
  ): StocatsticBootstrap {
    WorkflowRunner.engine = engine
    WorkflowRunner.repository = repo
    WorkflowRunner.scheduler = scheduler
    scheduler.syncAll()
    return StocatsticBootstrap
  }
}

object StocatsticBootstrap

