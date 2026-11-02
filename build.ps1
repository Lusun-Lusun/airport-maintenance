$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$taskWorkspace = $projectRoot
$javaExe = Join-Path $projectRoot '.runtime/tools/jdk17.0.21_0/bin/java.exe'
$mavenRoot = Join-Path $projectRoot '.runtime/tools/apache-maven-3.9.9'
$env:JAVA_HOME = Split-Path (Split-Path $javaExe -Parent) -Parent
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
if (!(Test-Path -LiteralPath $javaExe)) { throw 'Portable Java missing. Install Java 17 and Maven, then run: mvn verify' }
& $javaExe "-Dmaven.home=$mavenRoot" "-Dclassworlds.conf=$mavenRoot/bin/m2.conf" "-Dmaven.multiModuleProjectDirectory=$projectRoot" -classpath "$mavenRoot/boot/*" org.codehaus.plexus.classworlds.launcher.Launcher -B -ntp -f "$projectRoot/pom.xml" "-Dmaven.repo.local=$projectRoot/.runtime/m2" verify
if ($LASTEXITCODE -ne 0) { throw 'Build/test failed' }
