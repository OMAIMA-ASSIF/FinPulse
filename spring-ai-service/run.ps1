# Charge MISTRAL_API_KEY (et SERVER_PORT) depuis FinPulse/.env puis démarre Spring Boot.
$ErrorActionPreference = "Stop"
$rootEnv = Join-Path $PSScriptRoot ".." ".env" | Resolve-Path -ErrorAction SilentlyContinue

if ($rootEnv) {
    Get-Content $rootEnv | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        if ($line -notmatch "=") { return }
        $name, $value = $line -split "=", 2
        $name = $name.Trim()
        $value = $value.Trim().Trim('"').Trim("'")
        if ($name -in @("MISTRAL_API_KEY", "MISTRAL_CHAT_MODEL", "SERVER_PORT") -and $value) {
            Set-Item -Path "env:$name" -Value $value
        }
    }
}

if (-not $env:MISTRAL_API_KEY) {
    Write-Host "ERREUR: MISTRAL_API_KEY est vide." -ForegroundColor Red
    Write-Host "Ajoutez dans FinPulse/.env :"
    Write-Host "  MISTRAL_API_KEY=votre_cle_mistral"
    Write-Host "Ou : `$env:MISTRAL_API_KEY='votre_cle'; .\run.ps1"
    exit 1
}

if (-not $env:SERVER_PORT) { $env:SERVER_PORT = "8081" }

Write-Host "Demarrage sur http://localhost:$($env:SERVER_PORT) ..."
Set-Location $PSScriptRoot
mvn -q spring-boot:run
