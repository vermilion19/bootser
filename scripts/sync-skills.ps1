[CmdletBinding()]
param(
    [switch]$Prune,
    [switch]$SkipPull
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
Set-Location $repoRoot

if (-not $SkipPull) {
    Write-Host "[1/2] Running git pull..."
    git pull
    if ($LASTEXITCODE -ne 0) {
        throw "git pull failed with exit code $LASTEXITCODE"
    }
}

Write-Host "[2/2] Syncing skills to $HOME/.codex/skills..."
$syncScript = Join-Path $scriptDir 'sync-claude-skills-to-codex.ps1'
$sourceRoot = Join-Path $repoRoot '.claude/skills'
$targetRoot = Join-Path $HOME '.codex/skills'

if ($Prune) {
    & $syncScript -SourceRoot $sourceRoot -TargetRoot $targetRoot -DeleteRemoved
} else {
    & $syncScript -SourceRoot $sourceRoot -TargetRoot $targetRoot
}

Write-Host 'Done.'
