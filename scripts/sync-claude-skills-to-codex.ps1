[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$SourceRoot,
    [string]$TargetRoot = "$HOME/.codex/skills",
    [switch]$DeleteRemoved
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-AbsolutePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Get-DisplayName {
    param([Parameter(Mandatory = $true)][string]$SkillName)

    $acronyms = @('api', 'ui', 'db', 'pr', 'sql', 'mcp', 'ci', 'cli')
    $smallWords = @('and', 'or', 'to', 'up', 'with')

    $parts = $SkillName -split '-'
    $formatted = @()

    for ($i = 0; $i -lt $parts.Count; $i++) {
        $part = $parts[$i]
        if ([string]::IsNullOrWhiteSpace($part)) {
            continue
        }

        $lower = $part.ToLowerInvariant()
        if ($acronyms -contains $lower) {
            $formatted += $lower.ToUpperInvariant()
            continue
        }

        if ($i -gt 0 -and $smallWords -contains $lower) {
            $formatted += $lower
            continue
        }

        $formatted += ($part.Substring(0, 1).ToUpperInvariant() + $part.Substring(1).ToLowerInvariant())
    }

    return ($formatted -join ' ')
}

function Get-ShortDescription {
    param([Parameter(Mandatory = $true)][string]$DisplayName)

    $description = "Help with $DisplayName tasks and workflows"

    if ($description.Length -gt 64) {
        $description = "Help with $DisplayName tasks"
    }
    if ($description.Length -gt 64) {
        $description = "$DisplayName helper"
    }
    if ($description.Length -lt 25) {
        $description = "$DisplayName workflows helper"
    }
    if ($description.Length -gt 64) {
        $description = $description.Substring(0, 64).TrimEnd()
    }

    return $description
}

function Escape-YamlString {
    param([Parameter(Mandatory = $true)][string]$Value)

    return $Value.Replace('\\', '\\\\').Replace('"', '\\"')
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Write-OpenAiYaml {
    param(
        [Parameter(Mandatory = $true)][string]$SkillDir,
        [Parameter(Mandatory = $true)][string]$SkillName
    )

    $displayName = Get-DisplayName -SkillName $SkillName
    $shortDescription = Get-ShortDescription -DisplayName $displayName
    $defaultPrompt = "Use `$$SkillName to handle this task from start to finish."

    $agentsDir = Join-Path $SkillDir 'agents'
    New-Item -ItemType Directory -Path $agentsDir -Force | Out-Null

    $yaml = @(
        'interface:',
        ('  display_name: "{0}"' -f (Escape-YamlString -Value $displayName)),
        ('  short_description: "{0}"' -f (Escape-YamlString -Value $shortDescription)),
        ('  default_prompt: "{0}"' -f (Escape-YamlString -Value $defaultPrompt))
    ) -join "`n"

    Write-Utf8NoBom -Path (Join-Path $agentsDir 'openai.yaml') -Content ($yaml + "`n")
}

if (-not $SourceRoot) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
    $SourceRoot = Join-Path $repoRoot '.claude/skills'
}

$SourceRoot = Resolve-AbsolutePath -Path $SourceRoot
$TargetRoot = Resolve-AbsolutePath -Path $TargetRoot

if (-not (Test-Path -LiteralPath $SourceRoot)) {
    throw "SourceRoot not found: $SourceRoot"
}

$sourceSkillDirs = Get-ChildItem -LiteralPath $SourceRoot -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'SKILL.md') }

if (-not (Test-Path -LiteralPath $TargetRoot)) {
    if ($PSCmdlet.ShouldProcess($TargetRoot, 'Create target root directory')) {
        New-Item -ItemType Directory -Path $TargetRoot -Force | Out-Null
    }
}

$sourceSkillNames = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::OrdinalIgnoreCase)
$synced = 0

foreach ($srcDir in $sourceSkillDirs) {
    $skillName = $srcDir.Name
    $null = $sourceSkillNames.Add($skillName)

    $srcSkillFile = Join-Path $srcDir.FullName 'SKILL.md'
    $dstSkillDir = Join-Path $TargetRoot $skillName
    $dstSkillFile = Join-Path $dstSkillDir 'SKILL.md'

    $raw = Get-Content -LiteralPath $srcSkillFile -Raw -Encoding UTF8
    $normalized = [regex]::Replace($raw, '(?m)^user-invocable:\s*.*\r?\n', '')

    if ($PSCmdlet.ShouldProcess($dstSkillDir, 'Sync SKILL.md and agents/openai.yaml')) {
        New-Item -ItemType Directory -Path $dstSkillDir -Force | Out-Null
        Write-Utf8NoBom -Path $dstSkillFile -Content $normalized
        Write-OpenAiYaml -SkillDir $dstSkillDir -SkillName $skillName
        $synced++
    }
}

$deleted = 0
if ($DeleteRemoved) {
    $targetSkillDirs = Get-ChildItem -LiteralPath $TargetRoot -Directory |
        Where-Object { $_.Name -ne '.system' }

    foreach ($targetDir in $targetSkillDirs) {
        if (-not $sourceSkillNames.Contains($targetDir.Name)) {
            if ($PSCmdlet.ShouldProcess($targetDir.FullName, 'Remove skill directory not in source')) {
                Remove-Item -LiteralPath $targetDir.FullName -Recurse -Force
                $deleted++
            }
        }
    }
}

Write-Output ("Synced {0} skill(s) from '{1}' to '{2}'." -f $synced, $SourceRoot, $TargetRoot)
if ($DeleteRemoved) {
    Write-Output ("Removed {0} stale skill(s) from target." -f $deleted)
}
