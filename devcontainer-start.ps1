$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

Write-Host "Starting devcontainer..." -ForegroundColor Cyan
$upOutput = & devcontainer up --workspace-folder . | Out-String
Write-Host $upOutput

$match = [regex]::Match($upOutput, '"containerId"\s*:\s*"([0-9a-f]+)"')
if (-not $match.Success) { throw "Could not find containerId in devcontainer up output." }
$containerId = $match.Groups[1].Value
Write-Host "Container: $containerId" -ForegroundColor Green

Write-Host "Copying Claude credentials..." -ForegroundColor Cyan
docker cp "$env:USERPROFILE\.claude\.credentials.json" "${containerId}:/home/node/.claude/.credentials.json"
docker exec -u root $containerId sh -c "chown node:node /home/node/.claude/.credentials.json && chmod 600 /home/node/.claude/.credentials.json"

Write-Host "Marking onboarding complete in container .claude.json..." -ForegroundColor Cyan
docker exec -u root $containerId rm -f /home/node/.claude.json
$existing = & docker exec -u node $containerId sh -c "cat /home/node/.claude/.claude.json 2>/dev/null || echo '{}'" | Out-String
$cfg = $existing | ConvertFrom-Json
$hostClaude = Get-Content "$env:USERPROFILE\.claude.json" -Raw | ConvertFrom-Json
if (-not $cfg.oauthAccount) { $cfg | Add-Member -NotePropertyName oauthAccount -NotePropertyValue $hostClaude.oauthAccount -Force }
$cfg | Add-Member -NotePropertyName hasCompletedOnboarding -NotePropertyValue $true -Force
$cfg | Add-Member -NotePropertyName hasSeenTasksHint -NotePropertyValue $true -Force
$cfg | Add-Member -NotePropertyName isQualifiedForDataSharing -NotePropertyValue $false -Force

$tmp = Join-Path $env:TEMP 'claude-cfg.json'
$json = $cfg | ConvertTo-Json -Depth 20 -Compress
[System.IO.File]::WriteAllText($tmp, $json, [System.Text.UTF8Encoding]::new($false))
docker cp $tmp "${containerId}:/home/node/.claude/.claude.json"
docker exec -u root $containerId sh -c "chown node:node /home/node/.claude/.claude.json && chmod 600 /home/node/.claude/.claude.json"
Remove-Item $tmp

Write-Host ""
Write-Host "Opening bash. Type: claude --dangerously-skip-permissions" -ForegroundColor Yellow
Write-Host ""
devcontainer exec --workspace-folder . bash
