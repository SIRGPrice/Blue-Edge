# Launcher for the official asset region editor desktop tool.
#
# Usage (from anywhere):
#     .\scripts\install_stocatstic_assets.ps1
#     .\scripts\install_stocatstic_assets.ps1 -Validate
#
# This script is intentionally thin — all logic lives in :tools:asset-region-editor.

param(
  [switch]$Validate
)

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
