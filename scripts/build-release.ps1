$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$signingDir = Join-Path $env:USERPROFILE '.opentune-signing'
$secret = Get-Content -LiteralPath (Join-Path $signingDir 'password.dpapi') | ConvertTo-SecureString
$env:OPENTUNE_KEYSTORE = Join-Path $signingDir 'duffed-release.p12'
$env:OPENTUNE_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:OPENTUNE_KEY_PASSWORD = $env:OPENTUNE_STORE_PASSWORD
$env:OPENTUNE_KEY_ALIAS = 'opentune'
try {
    Push-Location $repo
    & ./gradlew.bat :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    $version = (Get-Content VERSION -Raw).Trim()
    Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination "app/build/outputs/apk/release/opentune-$version.apk"
} finally {
    Pop-Location
    Remove-Item Env:OPENTUNE_STORE_PASSWORD, Env:OPENTUNE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
