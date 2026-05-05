# Copyright 2026 SIRGPrice
#
# This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
#
# Licensed under the Blue Edge Custom License 1.0.
# You may not use this file except in compliance with that license.
# GitHub may host, cache, display, and facilitate collaboration on this file
# as required by the GitHub Terms of Service.
# See the repository root: LICENSE.md

param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot),
  [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$blueEdgeRepo = 'https://github.com/SIRGPrice/Blue-Edge'
$googleRepo = 'https://github.com/google-ai-edge/gallery'

function Get-Text {
  param([string]$Path)
  Get-Content -LiteralPath $Path -Raw
}

function Write-Text {
  param([string]$Path, [string]$Content)
  if (-not $WhatIf) {
    Set-Content -LiteralPath $Path -Value $Content -NoNewline
  }
}

function Get-GoogleCopyrightLine {
  param([string]$Content)
  $m = [regex]::Match($Content, 'Copyright\s+[^\r\n]*Google LLC')
  if ($m.Success) { return $m.Value.Trim() }
  return $null
}

function New-AgentBlockHeader {
  param([string]$GoogleCopyrightLine)
  if (-not $GoogleCopyrightLine) {
    throw "Google copyright line is required for AgentTools-style header."
  }
  @"
/*
 * $GoogleCopyrightLine & Modifications Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: $blueEdgeRepo,
 * a heavily modified fork of Google AI Edge Gallery: $googleRepo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
"@
}

function New-AgentXmlHeader {
  param([string]$GoogleCopyrightLine)
  if (-not $GoogleCopyrightLine) {
    throw "Google copyright line is required for AgentTools-style header."
  }
  @"
<!--
 $GoogleCopyrightLine & Modifications Copyright 2026 SIRGPrice

 This file is part of Blue Edge: $blueEdgeRepo,
 a heavily modified fork of Google AI Edge Gallery: $googleRepo

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at:

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
"@
}

function New-AgentHashHeader {
  param([string]$GoogleCopyrightLine)
  if (-not $GoogleCopyrightLine) {
    throw "Google copyright line is required for AgentTools-style header."
  }
  @"
# $GoogleCopyrightLine & Modifications Copyright 2026 SIRGPrice
#
# This file is part of Blue Edge: $blueEdgeRepo,
# a heavily modified fork of Google AI Edge Gallery: $googleRepo
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"@
}

function New-CustomBlockHeader {
  @"
/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: $blueEdgeRepo
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: LICENSE.md
 */
"@
}

function New-CustomXmlHeader {
  @"
<!--
 Copyright 2026 SIRGPrice

 This file is part of Blue Edge: $blueEdgeRepo

 Licensed under the Blue Edge Custom License 1.0.
 You may not use this file except in compliance with that license.
 GitHub may host, cache, display, and facilitate collaboration on this file
 as required by the GitHub Terms of Service.
 See the repository root: LICENSE.md
-->
"@
}

function New-CustomHashHeader {
  @"
# Copyright 2026 SIRGPrice
#
# This file is part of Blue Edge: $blueEdgeRepo
#
# Licensed under the Blue Edge Custom License 1.0.
# You may not use this file except in compliance with that license.
# GitHub may host, cache, display, and facilitate collaboration on this file
# as required by the GitHub Terms of Service.
# See the repository root: LICENSE.md
"@
}

function New-CustomMarkdownHeader {
  @"
<!--
Copyright 2026 SIRGPrice

This file is part of Blue Edge: $blueEdgeRepo

Licensed under the Blue Edge Custom License 1.0.
You may not use this file except in compliance with that license.
GitHub may host, cache, display, and facilitate collaboration on this file
as required by the GitHub Terms of Service.
See the repository root: LICENSE.md
-->

"@
}

function New-ThirdPartyHashHeader {
  param([string]$Note)
  @"
# Third-party notice preserved by Blue Edge.
# $Note
# This file is not owned by SIRGPrice and is not relicensed by Blue Edge.
# Original attribution and terms appear below.

"@
}

function Replace-TopBlockComment {
  param([string]$Content, [string]$Header)
  if ([regex]::IsMatch($Content, '^\s*(/\*.*?\*/\s*)+', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    return [regex]::Replace($Content, '^\s*(/\*.*?\*/\s*)+', "$Header`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }
  return "$Header`r`n$content"
}

function Replace-TopXmlComment {
  param([string]$Content, [string]$Header)
  $decl = [regex]::Match($Content, '^(<\?xml[^>]+\?>\s*)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  $prefix = $decl.Groups[1].Value
  $rest = if ($prefix) { $Content.Substring($prefix.Length) } else { $Content }
  if ([regex]::IsMatch($rest, '^\s*(<!--.*?-->\s*)+', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $rest = [regex]::Replace($rest, '^\s*(<!--.*?-->\s*)+', "$Header`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  } else {
    $rest = "$Header`r`n$rest"
  }
  return "$prefix$rest"
}

function Replace-TopHashComment {
  param([string]$Content, [string]$Header)
  if ([regex]::IsMatch($Content, '^(#.*(?:\r?\n#.*)*)\r?\n\r?\n', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    return [regex]::Replace($Content, '^(#.*(?:\r?\n#.*)*)\r?\n\r?\n', "$Header`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }
  return "$Header`r`n$content"
}

function Replace-TopMarkdownComment {
  param([string]$Content, [string]$Header)
  if ([regex]::IsMatch($Content, '^\s*<!--.*?-->\s*', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    return [regex]::Replace($Content, '^\s*<!--.*?-->\s*', $Header, [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }
  return "$Header$content"
}

function Update-FileWithHeader {
  param(
    [string]$Path,
    [ValidateSet('agent-block','agent-xml','agent-hash','custom-block','custom-xml','custom-hash','custom-md','thirdparty-hash')]
    [string]$Kind,
    [string]$ThirdPartyNote
  )
  $content = Get-Text -Path $Path
  switch ($Kind) {
    'agent-block' {
      $header = New-AgentBlockHeader -GoogleCopyrightLine (Get-GoogleCopyrightLine -Content $content)
      $updated = Replace-TopBlockComment -Content $content -Header $header.TrimEnd()
    }
    'agent-xml' {
      $google = Get-GoogleCopyrightLine -Content $content
      if (-not $google) { $google = 'Copyright 2026 Google LLC' }
      $header = New-AgentXmlHeader -GoogleCopyrightLine $google
      $updated = Replace-TopXmlComment -Content $content -Header $header.TrimEnd()
    }
    'agent-hash' {
      $header = New-AgentHashHeader -GoogleCopyrightLine (Get-GoogleCopyrightLine -Content $content)
      $updated = Replace-TopHashComment -Content $content -Header $header.TrimEnd()
    }
    'custom-block' {
      $updated = Replace-TopBlockComment -Content $content -Header (New-CustomBlockHeader).TrimEnd()
    }
    'custom-xml' {
      $updated = Replace-TopXmlComment -Content $content -Header (New-CustomXmlHeader).TrimEnd()
    }
    'custom-hash' {
      $updated = Replace-TopHashComment -Content $content -Header (New-CustomHashHeader).TrimEnd()
    }
    'custom-md' {
      $updated = Replace-TopMarkdownComment -Content $content -Header (New-CustomMarkdownHeader)
    }
    'thirdparty-hash' {
      $updated = Replace-TopHashComment -Content $content -Header (New-ThirdPartyHashHeader -Note $ThirdPartyNote).TrimEnd()
    }
  }
  if ($updated -ne $content) {
    Write-Text -Path $Path -Content $updated
    return $true
  }
  return $false
}

$updated = [System.Collections.Generic.List[string]]::new()

# 1) Normalize existing Google/Apache headers to AgentTools-style.
$googleFiles = Get-ChildItem (Join-Path $Root 'app'), (Join-Path $Root 'shared'), (Join-Path $Root 'iosApp'), (Join-Path $Root 'tools'), (Join-Path $Root 'scripts') -Recurse -File |
  Where-Object {
    $_.FullName -notmatch '\\build\\' -and
    (Select-String -Path $_.FullName -Pattern 'Copyright\s+[^\r\n]*Google LLC' -Quiet)
  }

foreach ($file in $googleFiles) {
  $kind = switch ($file.Extension.ToLowerInvariant()) {
    '.kt' { 'agent-block' }
    '.kts' { 'agent-block' }
    '.proto' { 'agent-block' }
    '.xml' { 'agent-xml' }
    '.gitignore' { 'agent-hash' }
    default { $null }
  }
  if ($kind -and (Update-FileWithHeader -Path $file.FullName -Kind $kind)) {
    $updated.Add($file.FullName)
  }
}

# 2) Add custom license headers to clearly Blue Edge-owned files that lacked a legal header.
$customBlockFiles = @(
  'app\src\main\java\com\google\ai\edge\gallery\data\Gemma4Support.kt',
  'app\src\main\java\com\google\ai\edge\gallery\ui\common\chat\MessageBodyComposite.kt',
  'app\src\main\java\com\google\ai\edge\gallery\ui\common\chat\rag\DocumentAttachment.kt',
  'app\src\main\java\com\google\ai\edge\gallery\customtasks\stocatstic\ui\assets\AssetCatalogs.generated.kt',
  'tools\asset-region-editor\build.gradle.kts',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\Main.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\cli\ValidateMain.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\codegen\KotlinCatalogGenerator.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\io\AssetIndex.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\io\AssetRegionsIo.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\io\RepoPaths.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\model\Model.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\ui\CanvasPanel.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\ui\EditorFrame.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\ui\components\Components.kt',
  'tools\asset-region-editor\src\main\kotlin\com\blueedge\assettool\ui\theme\EditorTheme.kt'
)
foreach ($rel in $customBlockFiles) {
  $path = Join-Path $Root $rel
  if (Test-Path $path) {
    if (Update-FileWithHeader -Path $path -Kind 'custom-block') { $updated.Add($path) }
  }
}

$customXmlFiles = @(
  'shared\src\androidMain\AndroidManifest.xml',
  'iosApp\BlueEdge\Info.plist'
)
foreach ($rel in $customXmlFiles) {
  $path = Join-Path $Root $rel
  if (Test-Path $path) {
    if (Update-FileWithHeader -Path $path -Kind 'custom-xml') { $updated.Add($path) }
  }
}

$customMarkdownFiles = @(
  'iosApp\README.md',
  'tools\asset-region-editor\README.md'
)
foreach ($rel in $customMarkdownFiles) {
  $path = Join-Path $Root $rel
  if (Test-Path $path) {
    if (Update-FileWithHeader -Path $path -Kind 'custom-md') { $updated.Add($path) }
  }
}

$customHashFiles = @(
  'scripts\install_stocatstic_assets.ps1',
  'scripts\update_attribution_headers.ps1',
  'scripts\apply_blueedge_license_policy.ps1'
)
foreach ($rel in $customHashFiles) {
  $path = Join-Path $Root $rel
  if (Test-Path $path) {
    if (Update-FileWithHeader -Path $path -Kind 'custom-hash') { $updated.Add($path) }
  }
}

# 3) Add AgentTools-style Apache headers to no-header drawable XML files in the derived app module.
$drawableFiles = Get-ChildItem (Join-Path $Root 'app\src\main\res\drawable') -Filter '*.xml' -File -ErrorAction SilentlyContinue
foreach ($file in $drawableFiles) {
  $content = Get-Text -Path $file.FullName
  if ($content -notmatch 'Apache License|Blue Edge Custom License|Copyright') {
    if (Update-FileWithHeader -Path $file.FullName -Kind 'agent-xml') { $updated.Add($file.FullName) }
  }
}

# 4) Preserve third-party notices with an explicit non-relicensing header.
$thirdPartyFiles = @{
  'app\src\main\assets\stocatstic\CREDITS.txt' = 'This document records third-party asset authorship and license terms bundled with Blue Edge.'
  'app\src\main\assets\stocatstic\Readme_AutoTile.txt' = 'This document records third-party AutoTile usage notes bundled with Blue Edge.'
}
foreach ($rel in $thirdPartyFiles.Keys) {
  $path = Join-Path $Root $rel
  if (Test-Path $path) {
    if (Update-FileWithHeader -Path $path -Kind 'thirdparty-hash' -ThirdPartyNote $thirdPartyFiles[$rel]) { $updated.Add($path) }
  }
}

"Updated $($updated.Count) files."
$updated | Sort-Object | ForEach-Object { $_ }

