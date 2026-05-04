# Copyright 2026 SIRGPrice
#
# This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
#
# Licensed under the Blue Edge Custom License 1.0.
# You may not use this file except in compliance with that license.
# GitHub may host, cache, display, and facilitate collaboration on this file
# as required by the GitHub Terms of Service.
# See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
param(
  [string]$Root = "C:\Users\tonch\Desktop\BlueEdge",
  [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-BlockHeader {
  param(
    [string]$OriginalCopyrightLine,
    [string]$CommentPrefix = ' * ',
    [string]$Open = '/*',
    [string]$Close = ' */'
  )

  $lines = [System.Collections.Generic.List[string]]::new()
  $lines.Add($Open)
  if ($OriginalCopyrightLine) {
    $lines.Add("${CommentPrefix}$OriginalCopyrightLine")
    $lines.Add("${CommentPrefix}Modifications Copyright 2026 SIRGPrice and Blue Edge contributors")
  } else {
    $lines.Add("${CommentPrefix}Copyright 2026 SIRGPrice and Blue Edge contributors")
    $lines.Add("${CommentPrefix}Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.")
    $lines.Add("${CommentPrefix}Upstream project originally published by Google LLC:")
    $lines.Add("${CommentPrefix}https://github.com/google-ai-edge/gallery")
  }
  $lines.Add("${CommentPrefix}")
  if ($OriginalCopyrightLine) {
    $lines.Add("${CommentPrefix}This file is part of Blue Edge, a heavily modified fork of Google AI Edge Gallery:")
    $lines.Add("${CommentPrefix}https://github.com/google-ai-edge/gallery")
    $lines.Add("${CommentPrefix}")
  }
  $lines.Add("${CommentPrefix}Licensed under the Apache License, Version 2.0 (the `"License`");")
  $lines.Add("${CommentPrefix}you may not use this file except in compliance with the License.")
  $lines.Add("${CommentPrefix}You may obtain a copy of the License at")
  $lines.Add("${CommentPrefix}")
  $lines.Add("${CommentPrefix}    http://www.apache.org/licenses/LICENSE-2.0")
  $lines.Add("${CommentPrefix}")
  $lines.Add("${CommentPrefix}Unless required by applicable law or agreed to in writing, software")
  $lines.Add("${CommentPrefix}distributed under the License is distributed on an `"AS IS`" BASIS,")
  $lines.Add("${CommentPrefix}WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.")
  $lines.Add("${CommentPrefix}See the License for the specific language governing permissions and")
  $lines.Add("${CommentPrefix}limitations under the License.")
  $lines.Add($Close)
  return ($lines -join "`r`n")
}

function New-XmlHeader {
  param([string]$OriginalCopyrightLine)

  $lines = [System.Collections.Generic.List[string]]::new()
  $lines.Add('<!--')
  if ($OriginalCopyrightLine) {
    $lines.Add(" $OriginalCopyrightLine")
    $lines.Add(' Modifications Copyright 2026 SIRGPrice and Blue Edge contributors')
  } else {
    $lines.Add(' Copyright 2026 SIRGPrice and Blue Edge contributors')
    $lines.Add(' Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.')
    $lines.Add(' Upstream project originally published by Google LLC:')
    $lines.Add(' https://github.com/google-ai-edge/gallery')
  }
  $lines.Add('')
  if ($OriginalCopyrightLine) {
    $lines.Add(' This file is part of Blue Edge, a heavily modified fork of Google AI Edge Gallery:')
    $lines.Add(' https://github.com/google-ai-edge/gallery')
    $lines.Add('')
  }
  $lines.Add(' Licensed under the Apache License, Version 2.0 (the "License");')
  $lines.Add(' you may not use this file except in compliance with the License.')
  $lines.Add(' You may obtain a copy of the License at')
  $lines.Add('')
  $lines.Add('     http://www.apache.org/licenses/LICENSE-2.0')
  $lines.Add('')
  $lines.Add(' Unless required by applicable law or agreed to in writing, software')
  $lines.Add(' distributed under the License is distributed on an "AS IS" BASIS,')
  $lines.Add(' WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.')
  $lines.Add(' See the License for the specific language governing permissions and')
  $lines.Add(' limitations under the License.')
  $lines.Add('-->')
  return ($lines -join "`r`n")
}

function New-HashHeader {
  param([string]$OriginalCopyrightLine)

  $lines = [System.Collections.Generic.List[string]]::new()
  $lines.Add('# @license')
  if ($OriginalCopyrightLine) {
    $lines.Add("# $OriginalCopyrightLine")
    $lines.Add('# Modifications Copyright 2026 SIRGPrice and Blue Edge contributors')
  } else {
    $lines.Add('# Copyright 2026 SIRGPrice and Blue Edge contributors')
    $lines.Add('# Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.')
    $lines.Add('# Upstream project originally published by Google LLC:')
    $lines.Add('# https://github.com/google-ai-edge/gallery')
  }
  $lines.Add('#')
  if ($OriginalCopyrightLine) {
    $lines.Add('# This file is part of Blue Edge, a heavily modified fork of Google AI Edge Gallery:')
    $lines.Add('# https://github.com/google-ai-edge/gallery')
    $lines.Add('#')
  }
  $lines.Add('# Licensed under the Apache License, Version 2.0 (the "License");')
  $lines.Add('# you may not use this file except in compliance with the License.')
  $lines.Add('# You may obtain a copy of the License at')
  $lines.Add('#')
  $lines.Add('#     http://www.apache.org/licenses/LICENSE-2.0')
  $lines.Add('#')
  $lines.Add('# Unless required by applicable law or agreed to in writing, software')
  $lines.Add('# distributed under the License is distributed on an "AS IS" BASIS,')
  $lines.Add('# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.')
  $lines.Add('# See the License for the specific language governing permissions and')
  $lines.Add('# limitations under the License.')
  $lines.Add('# ==============================================================================')
  return ($lines -join "`r`n")
}

function Get-OriginalCopyrightLine {
  param([string]$Content)

  $match = [regex]::Match($Content, 'Copyright\s+[^\r\n]*Google LLC')
  if ($match.Success) {
    return $match.Value.Trim()
  }
  return $null
}

function Update-BlockCommentFile {
  param([string]$Path)

  $content = Get-Content -LiteralPath $Path -Raw
  $header = New-BlockHeader -OriginalCopyrightLine (Get-OriginalCopyrightLine -Content $content)

  if ([regex]::IsMatch($content, '^\s*(/\*.*?\*/\s*)+', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $updated = [regex]::Replace($content, '^\s*(/\*.*?\*/\s*)+', "$header`r`n`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  } else {
    $updated = "$header`r`n`r`n$content"
  }

  if ($updated -ne $content -and -not $WhatIf) {
    Set-Content -LiteralPath $Path -Value $updated -NoNewline
  }
  return ($updated -ne $content)
}

function Update-XmlFile {
  param([string]$Path)

  $content = Get-Content -LiteralPath $Path -Raw
  $declarationMatch = [regex]::Match($content, '^(<\?xml[^>]+\?>\s*)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  $declaration = $declarationMatch.Groups[1].Value
  $rest = if ($declaration) { $content.Substring($declaration.Length) } else { $content }
  $header = New-XmlHeader -OriginalCopyrightLine (Get-OriginalCopyrightLine -Content $content)

  if ([regex]::IsMatch($rest, '^\s*(<!--.*?-->\s*)+', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $newRest = [regex]::Replace($rest, '^\s*(<!--.*?-->\s*)+', "$header`r`n`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  } else {
    $newRest = "$header`r`n`r`n$rest"
  }

  $updated = "$declaration$newRest"
  if ($updated -ne $content -and -not $WhatIf) {
    Set-Content -LiteralPath $Path -Value $updated -NoNewline
  }
  return ($updated -ne $content)
}

function Update-HashCommentFile {
  param([string]$Path)

  $content = Get-Content -LiteralPath $Path -Raw
  $header = New-HashHeader -OriginalCopyrightLine (Get-OriginalCopyrightLine -Content $content)
  $updated = [regex]::Replace($content, '^(#.*(?:\r?\n#.*)*)\r?\n\r?\n', "$header`r`n`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  if ($updated -eq $content) {
    $updated = "$header`r`n`r`n$content"
  }

  if ($updated -ne $content -and -not $WhatIf) {
    Set-Content -LiteralPath $Path -Value $updated -NoNewline
  }
  return ($updated -ne $content)
}

$targets = Get-ChildItem (Join-Path $Root 'app'), (Join-Path $Root 'shared'), (Join-Path $Root 'iosApp'), (Join-Path $Root 'tools') -Recurse -File |
  Where-Object {
    $_.FullName -notmatch '\\build\\' -and
    (Select-String -Path $_.FullName -Pattern 'Copyright 20[0-9]{2} Google LLC|Licensed under the Apache License, Version 2.0' -Quiet)
  }

$updatedFiles = [System.Collections.Generic.List[string]]::new()
foreach ($file in $targets) {
  $changed = switch ($file.Extension.ToLowerInvariant()) {
    '.kt' { Update-BlockCommentFile -Path $file.FullName }
    '.kts' { Update-BlockCommentFile -Path $file.FullName }
    '.proto' { Update-BlockCommentFile -Path $file.FullName }
    '.xml' { Update-XmlFile -Path $file.FullName }
    '.gitignore' { Update-HashCommentFile -Path $file.FullName }
    default { $false }
  }
  if ($changed) {
    $updatedFiles.Add($file.FullName)
  }
}

"Updated $($updatedFiles.Count) files."
$updatedFiles | Sort-Object | ForEach-Object { $_ }


