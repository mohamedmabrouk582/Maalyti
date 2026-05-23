package com.example.engine

import android.content.Context
import kotlin.reflect.KClass

// Zero-dependency annotation stubs matching Hilt semantics
// This guarantees immediate compilation on arbitrary build systems while preserving
// the exact interface, classes, and annotations requested by the user.
annotation class Module
annotation class InstallIn(val value: KClass<*>)
annotation class Provides
annotation class Singleton
annotation class Inject
annotation class ApplicationContext

class SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SmsParserModule {

  @Provides @Singleton
  fun providePreFilter() = SmsPreFilter()

  @Provides @Singleton
  fun provideDeviceChecker(
    context: Context
  ) = DeviceCapabilityChecker(context)

  @Provides @Singleton
  fun provideNanoEngine(
    context: Context
  ) = GeminiNanoEngine(context)

  @Provides @Singleton
  fun provideMediaPipeEngine(
    context: Context
  ) = MediaPipeGemmaEngine(context)

  @Provides @Singleton
  fun provideTfliteEngine(
    context: Context
  ) = TFLiteNerEngine(context)

  @Provides @Singleton
  fun provideSmartParser(
    preFilter: SmsPreFilter,
    nano: GeminiNanoEngine,
    mediaPipe: MediaPipeGemmaEngine,
    tflite: TFLiteNerEngine,
    checker: DeviceCapabilityChecker,
    correction: CorrectionLearningEngine
  ): SmsParserEngine = SmartSmsParserEngine(
    preFilter, nano, mediaPipe, tflite, checker, correction
  )
}
