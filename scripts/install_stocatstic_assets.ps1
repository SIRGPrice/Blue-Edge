# Copyright 2026 SIRGPrice
#
# This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
#
# Licensed under the Blue Edge Custom License 1.0.
# You may not use this file except in compliance with that license.
# GitHub may host, cache, display, and facilitate collaboration on this file
# as required by the GitHub Terms of Service.
# See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
  if ($Validate) {
    Write-Host 'Running headless validation of asset_regions.json ...' -ForegroundColor Cyan
    & .\gradlew.bat :tools:asset-region-editor:validateRegions
  } else {
    Write-Host 'Launching Blue Edge Asset Region Editor ...' -ForegroundColor Cyan
    & .\gradlew.bat :tools:asset-region-editor:run
  }
} finally {
  Pop-Location
}
