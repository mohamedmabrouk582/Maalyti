package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

class DeviceCapabilityChecker(private val context: Context) {

  private val prefs = context.getSharedPreferences("device_capability_cache", Context.MODE_PRIVATE)

  fun isGeminiNanoAvailable(): Boolean {
    val cached = prefs.getBoolean("gemini_nano_available", false)
    val lastCheck = prefs.getLong("gemini_nano_last_check", 0L)
    val now = System.currentTimeMillis()

    // Cache check for 24 hours
    if (now - lastCheck < 24 * 60 * 60 * 1000) {
      return cached
    }

    var available = false
    try {
      // Standard AICore check (GenerativeModel check / play services / FeatureCheckingRequest)
      // Usually requires importing "com.google.android.gms.tasks" or similar
      // Since we are running in a flexible runtime environment,
      // let's do safe reflection check for Android AICore or GMS Generative AI classes.
      val aiCoreClassPresent = try {
        Class.forName("com.google.android.gms.ai.AiFeatureManager") != null
      } catch (e: ClassNotFoundException) {
        // Also check if we have pixel/galaxy system service
        Build.MODEL.contains("Pixel", ignoreCase = true) || Build.MODEL.contains("SM-S92", ignoreCase = true)
      }
      available = aiCoreClassPresent
    } catch (e: Exception) {
      available = false
    }

    prefs.edit()
      .putBoolean("gemini_nano_available", available)
      .putLong("gemini_nano_last_check", now)
      .apply()

    return available
  }

  fun isMediaPipeCompatible(): Boolean {
    // RAM: Total RAM >= 4GB
    var ramOk = false
    try {
      val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val memInfo = ActivityManager.MemoryInfo()
      actManager.getMemoryInfo(memInfo)
      val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
      ramOk = totalRamGb >= 3.5 // give a little margin for 4GB RAM devices showing e.g. 3.8GB
    } catch (e: Exception) {
      ramOk = true
    }

    // Storage: StatFs freeBytes >= 1.5GB
    var storageOk = false
    try {
      val path = context.getExternalFilesDir(null) ?: context.filesDir
      val stat = StatFs(path.path)
      val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
      val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
      storageOk = freeGb >= 1.4 // margin
    } catch (e: Exception) {
      storageOk = true
    }

    // API: SDK >= 26
    val apiOk = Build.VERSION.SDK_INT >= 26

    // ABI: arm64-v8a in supported ABIs
    val abiOk = Build.SUPPORTED_ABIS.contains("arm64-v8a") || Build.SUPPORTED_ABIS.contains("x86_64")

    return ramOk && storageOk && apiOk && abiOk
  }

  fun isMediaPipeReady(): Boolean {
    return isMediaPipeCompatible() && GemmaModelManager.isDownloaded(context)
  }

  fun getActiveEngine(): ParserEngine {
    return when {
      isGeminiNanoAvailable() -> ParserEngine.GEMINI_NANO
      isMediaPipeReady() -> ParserEngine.GEMMA_MEDIAPIPE
      else -> ParserEngine.TFLITE_NER
    }
  }
}

object GemmaModelManager {
  fun isDownloaded(context: Context): Boolean {
    val modelDir = context.getExternalFilesDir("models/gemma") ?: return false
    val modelFile = File(modelDir, "gemma-2b-it-gpu-int4.bin")
    return modelFile.exists() && modelFile.length() > 500 * 1024 * 1024 // Greater than 500MB is downloaded
  }
}
