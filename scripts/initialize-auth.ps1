param([ValidateSet('dev','test')][string]$Environment = 'dev')
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$statePath = Join-Path $projectRoot ".tool-cache/auth/$Environment"
New-Item -ItemType Directory -Force $statePath | Out-Null
$configPath = Join-Path $projectRoot ('backend/config/application-auth' + $(if ($Environment -eq 'test') { '-test' } else { '' }) + '.yml')
$privatePath = Join-Path $statePath 'private.pem'
$publicPath = Join-Path $statePath 'public.pem'
if ((Test-Path $privatePath) -ne (Test-Path $publicPath)) { throw 'Incomplete key pair; restore both files before initialization.' }
if (!(Test-Path $privatePath)) {
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        [IO.File]::WriteAllText($privatePath, $rsa.ExportPkcs8PrivateKeyPem())
        [IO.File]::WriteAllText($publicPath, $rsa.ExportSubjectPublicKeyInfoPem())
    } finally { $rsa.Dispose() }
}
$redisPath = Join-Path $statePath 'redis.conf'
if (!(Test-Path $configPath)) {
    if (Test-Path $redisPath) { throw 'Redis configuration exists without application configuration; restore the matching configuration.' }
    $redisSecret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
    $hmacSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    $redisPort = if ($Environment -eq 'test') { 6381 } else { 6380 }
    $redisDirectory = $statePath.Replace('\','/')
    @"
bind 127.0.0.1
protected-mode yes
port $redisPort
requirepass $redisSecret
dir "$redisDirectory"
dbfilename auth.rdb
appendonly yes
appendfilename auth.aof
logfile "$redisDirectory/redis.log"
"@ | Set-Content -Encoding ascii $redisPath
    $privateUri = $privatePath.Replace('\','/')
    $publicUri = $publicPath.Replace('\','/')
    @"
spring:
  data:
    redis:
      host: 127.0.0.1
      port: $redisPort
      password: $redisSecret
      connect-timeout: 2s
      timeout: 2s
english-hero:
  auth:
    namespace: eh:${Environment}:auth:v1
    issuer: lingua-loop
    audience: lingua-loop-web
    kid: local-$Environment-v1
    private-key: '$privateUri'
    public-key: '$publicUri'
    hmac-secret: '$hmacSecret'
    allowed-origins: http://127.0.0.1:4174,http://127.0.0.1:5173
    secure: false
"@ | Set-Content -Encoding utf8 $configPath
} elseif (!(Test-Path $redisPath)) { throw 'Application configuration exists without matching Redis configuration; restore it.' }
Write-Output "Auth $Environment configuration ready; existing keys and credentials preserved."
