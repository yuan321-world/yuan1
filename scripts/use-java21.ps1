param([string]$JavaHome = 'C:\Java\jdk-21.0.12.1')
$ErrorActionPreference = 'Stop'
$resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$javaCommand = Join-Path $resolvedJavaHome 'bin\java.exe'
$javacCommand = Join-Path $resolvedJavaHome 'bin\javac.exe'
if (!(Test-Path -LiteralPath $javaCommand) -or !(Test-Path -LiteralPath $javacCommand)) {
  throw 'JavaHome must contain a complete JDK 21.'
}
$javaVersion = & $javaCommand -version 2>&1 | Out-String
$compilerVersion = & $javacCommand -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "21\.' -or $compilerVersion -notmatch 'javac 21\.') {
  throw 'This project requires Java and javac 21.'
}
$env:JAVA_HOME = $resolvedJavaHome
$env:PATH = (Join-Path $resolvedJavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
Write-Host "Current process uses JDK 21 at $resolvedJavaHome"
