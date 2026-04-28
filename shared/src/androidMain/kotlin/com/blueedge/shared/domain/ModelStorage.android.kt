/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.domain

import android.content.Context
import com.blueedge.shared.platform.AndroidContext
import java.io.File

private class AndroidModelStorage(private val ctx: Context) : ModelStorage {
  override val baseModelsDir: String =
    ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath

  override fun resolvePath(model: Model, fileName: String): String {
    if (model.imported) return baseModelsDir + File.separator + fileName
    if (model.localModelFilePathOverride.isNotEmpty()) return model.localModelFilePathOverride
    if (model.localFileRelativeDirPathOverride.isNotEmpty()) {
      return listOf(baseModelsDir, model.localFileRelativeDirPathOverride, fileName)
        .joinToString(File.separator)
    }
    val dir = listOf(baseModelsDir, model.normalizedName, model.version)
      .joinToString(File.separator)
    return if (model.isZip && model.unzipDir.isNotEmpty()) {
      listOf(dir, model.unzipDir).joinToString(File.separator)
    } else {
      listOf(dir, fileName).joinToString(File.separator)
    }
  }
}

actual fun provideModelStorage(): ModelStorage = AndroidModelStorage(AndroidContext.appContext)

