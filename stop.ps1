$pidFile = Join-Path $PSScriptRoot 'server.pid'
if (Test-Path -LiteralPath $pidFile) {
 $serverProcess = Get-CimInstance Win32_Process -Filter ('ProcessId = '+(Get-Content -LiteralPath $pidFile)) -ErrorAction SilentlyContinue
 if ($serverProcess -and $serverProcess.CommandLine -like ('*'+$PSScriptRoot+'*maintenance-1.0.0.jar*')) { Stop-Process -Id $serverProcess.ProcessId; Write-Host 'Stopped project server.' }
 else { Write-Host 'No matching project server process.' }
 Remove-Item -LiteralPath $pidFile
}
