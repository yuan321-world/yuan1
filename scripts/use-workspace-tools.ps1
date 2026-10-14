# Dot-source this script in a terminal used for this project.
# Configuration and writable caches stay in the ignored workspace directory.
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskCache = Join-Path $taskRoot '.tool-cache'
$env:XDG_CONFIG_HOME = Join-Path $taskCache 'config'
$env:XDG_CACHE_HOME = Join-Path $taskCache 'cache'
$env:XDG_STATE_HOME = Join-Path $taskCache 'state'
$env:MAVEN_USER_HOME = Join-Path $taskCache 'maven-home'
$taskRepository = Join-Path $taskCache 'maven-repository'
$taskBin = Join-Path $taskRoot 'frontend/node_modules/.bin'
if (($env:PATH -split ';') -notcontains $taskBin) { $env:PATH = $taskBin + ';' + $env:PATH }
foreach ($taskDirectory in @($env:XDG_CONFIG_HOME, $env:XDG_CACHE_HOME, $env:XDG_STATE_HOME, $env:MAVEN_USER_HOME, $taskRepository)) {
    New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
}
# Preserve existing JVM options and only override Maven's artifact cache path.
$taskOption = '-Dmaven.repo.local="' + $taskRepository + '"'
if (!$env:MAVEN_OPTS -or !$env:MAVEN_OPTS.EndsWith($taskOption)) {
    $env:MAVEN_OPTS = ($env:MAVEN_OPTS + ' ' + $taskOption).Trim()
}
Write-Host 'Project tool configuration and caches use .tool-cache (current terminal only).'
