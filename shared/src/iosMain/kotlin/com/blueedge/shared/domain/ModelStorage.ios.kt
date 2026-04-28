/*
 * Copyright 2026 Blue Edge contributors.
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.blueedge.shared.domain

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private class IosModelStorage : ModelStorage {

  override val baseModelsDir: String = run {
    val urls = NSFileManager.defaultManager.URLsForDirectory(
      directory = NSDocumentDirectory,
      inDomains = NSUserDomainMask,
    )
    val docs = urls.firstOrNull() as? NSURL
    docs?.path?.let { "$it/models" } ?: "/tmp/blueedge-models"
  }

  override fun resolvePath(model: Model, fileName: String): String {
    if (model.imported) return "$baseModelsDir/$fileName"
    if (model.localModelFilePathOverride.isNotEmpty()) return model.localModelFilePathOverride
    if (model.localFileRelativeDirPathOverride.isNotEmpty()) {
      return "$baseModelsDir/${model.localFileRelativeDirPathOverride}/$fileName"
    }
    val dir = "$baseModelsDir/${model.normalizedName}/${model.version}"
    return if (model.isZip && model.unzipDir.isNotEmpty()) {
      "$dir/${model.unzipDir}"
    } else {
      "$dir/$fileName"
    }
  }

  override fun listModelFiles(): List<ModelFile> {
    val manager = NSFileManager.defaultManager
    val names = manager.contentsOfDirectoryAtPath(baseModelsDir, error = null).orEmpty()
    return names.mapNotNull { raw ->
      val name = raw as? String ?: return@mapNotNull null
      val path = "$baseModelsDir/$name"
      if (!manager.fileExistsAtPath(path)) return@mapNotNull null
      val attrs = manager.attributesOfItemAtPath(path, error = null)
      val size = (attrs?.get(NSFileSize) as? Number)?.toLong() ?: 0L
      val type = attrs?.get(NSFileType)
      ModelFile(
        name = name,
        absolutePath = path,
        sizeInBytes = size,
        isDirectory = type == NSFileTypeDirectory,
      )
    }.sortedWith(compareByDescending<ModelFile> { it.isDirectory }.thenBy { it.name.lowercase() })
  }
}

actual fun provideModelStorage(): ModelStorage = IosModelStorage()

