param([ValidateSet('dev','test')][string]$Environment = 'dev', [ValidateSet('start','stop','status')][string]$Action = 'status')
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$statePath = [IO.Path]::GetFullPath((Join-Path $projectRoot ".tool-cache/auth/$Environment"))
if (!$statePath.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'State path outside workspace.' }
$configPath = Join-Path $statePath 'redis.conf'
$pidPath = Join-Path $statePath 'redis.pid'
$binary = 'D:\redis\redis-server.exe'
$owned = $null
if (Test-Path $pidPath) {
    $savedId = [int](Get-Content $pidPath -Raw)
    $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$savedId"
    if ($owned -and ($owned.ExecutablePath -ne $binary -or !$owned.CommandLine.Contains($configPath))) { throw 'PID belongs to another process; refusing to act.' }
}
if ($Action -eq 'stop') {
    if ($owned) {
        $secretLine = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^requirepass [a-f0-9]{64}$' }
        if (!$secretLine) { throw 'Project Redis credentials unavailable; refusing forced shutdown.' }
        $priorAuth = $env:REDISCLI_AUTH
        try {
            $env:REDISCLI_AUTH = $secretLine.Substring(12)
            $redisPort = if ($Environment -eq 'test') { 6381 } else { 6380 }
            & 'D:\redis\redis-cli.exe' -h 127.0.0.1 -p $redisPort shutdown save | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Graceful project Redis shutdown failed.' }
            $stopping = Get-Process -Id $owned.ProcessId -ErrorAction SilentlyContinue
            if ($stopping) { $stopping.WaitForExit(5000) | Out-Null }
            if (Get-Process -Id $owned.ProcessId -ErrorAction SilentlyContinue) { throw 'Project Redis has not stopped; no forced termination performed.' }
        } finally { $env:REDISCLI_AUTH = $priorAuth }
        Write-Output "Project Redis $Environment stopped and persisted."
    }
    if (Test-Path $pidPath) { Remove-Item -LiteralPath $pidPath }
} elseif ($Action -eq 'start') {
    if ($owned) { Write-Output "Project Redis $Environment already running."; return }
    if (!(Test-Path $configPath)) { throw 'Run initialize-auth.ps1 first.' }
    $redisPort = if ($Environment -eq 'test') { 6381 } else { 6380 }
    if (Get-NetTCPConnection -LocalPort $redisPort -State Listen -ErrorAction SilentlyContinue) { throw "Port $redisPort occupied; no process changed." }
    $started = Start-Process -FilePath $binary -ArgumentList ('"' + $configPath + '"') -WorkingDirectory $statePath -WindowStyle Hidden -PassThru
    Start-Sleep -Milliseconds 700
    if ($started.HasExited) { throw 'Project Redis failed to start; inspect project redis.log.' }
    $started.Id | Set-Content $pidPath
    Write-Output "Project Redis $Environment started on $redisPort (PID $($started.Id))."
} else { Write-Output "Project Redis $Environment running: $([bool]$owned)" }
