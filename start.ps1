param([switch]$Redis)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$taskWorkspace = Split-Path (Split-Path $projectRoot -Parent) -Parent
$javaExe = Join-Path $projectRoot '.runtime/tools/jdk17.0.21_0/bin/java.exe'
if (!(Test-Path -LiteralPath $javaExe)) { $javaExe = (Get-Command java -ErrorAction Stop).Source }
$jar = Join-Path $projectRoot 'target/maintenance-1.0.0.jar'
$useJar = Test-Path -LiteralPath $jar
if (!$useJar -and !(Test-Path -LiteralPath (Join-Path $projectRoot 'target/classes/airport/Application.class'))) { throw 'Build first: ./build.ps1 or run scripts/portable_build.py' }
$pidFile = Join-Path $projectRoot 'server.pid'
if (Test-Path -LiteralPath $pidFile) { $oldProcess = Get-Process -Id (Get-Content -LiteralPath $pidFile) -ErrorAction SilentlyContinue; if ($oldProcess) { Write-Host 'Server already running. http://127.0.0.1:8080'; return } }
$arguments = @('-Duser.timezone=UTC')
if ($useJar) { $arguments += @('-jar',('"'+$jar+'"')) }
else {
 $classpath = (Join-Path $projectRoot 'target/classes') + ';' + ((Get-ChildItem (Join-Path $projectRoot '.runtime/m2') -Recurse -Filter *.jar | Where-Object { $_.Name -notmatch 'sources|javadoc|google-java-format|slf4j-api-1.' } | ForEach-Object { $_.FullName }) -join ';')
 $arguments += @('-cp',('"'+$classpath+'"'),'airport.Application')
}
if ($Redis) { $arguments += '--airport.redis.enabled=true' }
$process = Start-Process -FilePath $javaExe -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot 'server.log') -RedirectStandardError (Join-Path $projectRoot 'server-error.log') -PassThru
$process.Id | Set-Content -LiteralPath $pidFile
Write-Host ('Started PID '+$process.Id+'; open http://127.0.0.1:8080; startup takes about 10 seconds.')
