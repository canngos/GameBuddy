# Runs the Maestro flows against the Android emulator.
#
#   .\run.ps1                 # all flows
#   .\run.ps1 -Flow 02        # just one
#   .\run.ps1 -SkipProvision  # reuse the last provisioned accounts
#   .\run.ps1 -ReleaseBuild   # against an EAS build rather than the debug APK
#
# Provisioning runs first and feeds the flows their starting state as `-e` variables:
# Maestro drives a screen, it cannot read a verification code out of Postgres or arrange a
# match between two accounts.

param(
  [string]$Flow = "",
  [switch]$SkipProvision,
  [string]$Device = "",
  # An EAS build carries its JS bundle inside the APK, so Metro is not merely unnecessary —
  # requiring it would be wrong, because a running Metro cannot serve a release build and its
  # presence would prove nothing. This is the mode for the pre-submission gate: the release
  # build is the only configuration that matters for a native crash, since a debug build
  # keeps React Native's dev-support machinery attached. See QA_FINDINGS.md #6.
  [switch]$ReleaseBuild
)

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot
$root = Resolve-Path "$PSScriptRoot\..\.."

$env:PATH = "C:\Users\canba\.maestro\bin;C:\Users\canba\AppData\Local\Android\Sdk\platform-tools;" + $env:PATH
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = "true"

function Get-HttpText {
  param([string]$Url)
  try {
    $r = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
    if ($r.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
  } catch { "" }
}

# --- preconditions ----------------------------------------------------------------------
# Each of these has failed at least once and produced a confusing error somewhere else, so
# they are checked up front where the message can say what is actually wrong.

$devices = & adb devices | Select-String "emulator|device$" | Where-Object { $_ -notmatch "List of" }
if (-not $devices) { throw "No device. Start the emulator: emulator -avd GameBuddy_API34" }

# The app is a debug build: the JS bundle is served by Metro at runtime and is NOT in the
# APK. Without Metro the app runs whatever bundle it cached last, so a code change appears
# to have no effect — which is a genuinely baffling ten minutes.
# `.Content` comes back as a byte array when the response carries no charset — which
# Metro's /status does not — so it has to be decoded before it can be matched. Comparing
# the raw value reports "Metro is not running" against a Metro that is running perfectly.
if ($ReleaseBuild) {
  Write-Host "release build: skipping the Metro check (the bundle is inside the APK)" -ForegroundColor DarkGray
} else {
  $metro = Get-HttpText "http://localhost:8081/status"
  if ($metro -notmatch "packager-status:running") {
    throw "Metro is not running. Start it:  cd GameBuddy-App; npx expo start --dev-client"
  }
}

# The emulator reaches the backend through these, not over the network.
& adb reverse tcp:8080 tcp:8080 | Out-Null
& adb reverse tcp:8081 tcp:8081 | Out-Null

# `127.0.0.1`, not `localhost`. On this machine `localhost` resolves to ::1 first and
# nothing is listening there for 8080, so this probe times out and reports a healthy
# backend as down — the failure looks like Docker is off when it is answering fine on IPv4.
# `qa/functional/helpers/api.js` has the same default and the same problem; export
# GB_BASE_URL=http://127.0.0.1:8080 for the functional suite.
$health = Get-HttpText "http://127.0.0.1:8080/actuator/health"
if ($health -notmatch '"status":"UP"') { throw "Backend is not up. Start it:  docker compose up -d" }

# 01-onboarding reads the verification code through this, because tapping "Email me a
# code" issues a new one and it cannot be known in advance.
# Any HTTP answer means it is listening. A 404 is the *correct* response to an address
# with no code stored, and Invoke-WebRequest throws on 404 — so testing for a body here
# reports a healthy helper as missing.
$codeSrvUp = $false
try {
  Invoke-WebRequest -Uri "http://127.0.0.1:8099/code?email=probe@qa.gamebuddy.invalid" `
    -TimeoutSec 5 -UseBasicParsing | Out-Null
  $codeSrvUp = $true
} catch {
  $codeSrvUp = ($null -ne $_.Exception.Response)
}
if (-not $codeSrvUp) {
  throw "Code helper is not running. Start it:  node qa/maestro/code-server.js"
}

# `takeScreenshot: artifacts/...` does NOT create its own parent directory. Maestro reports
# the step COMPLETED either way and writes nothing, so a missing folder looks exactly like a
# flow that stopped before reaching its screenshots — which is how the whole suite ran for a
# while with no evidence coming out of it.
if (-not (Test-Path "artifacts")) { New-Item -ItemType Directory -Path "artifacts" | Out-Null }

$reachable = if ($ReleaseBuild) { "emulator, backend and code helper" } else { "emulator, Metro, backend and code helper" }
Write-Host "$reachable all reachable" -ForegroundColor DarkGray

# --- provision ----------------------------------------------------------------------------
if ($SkipProvision -and (Test-Path ".env-args")) {
  $provArgs = (Get-Content ".env-args" -Raw).Trim()
  Write-Host "reusing the previous provisioned accounts" -ForegroundColor DarkGray
} else {
  Push-Location $root
  $provArgs = (& node qa/maestro/provision.js --args) -join " "
  Pop-Location
  if (-not $provArgs) { throw "provision.js produced nothing" }
  Set-Content -Path ".env-args" -Value $provArgs -Encoding utf8
}
$argList = $provArgs -split ' '

# --- run ------------------------------------------------------------------------------------
# Underscore-prefixed files are subflows, not tests.
$flows = Get-ChildItem -Filter "*.yaml" |
  Where-Object { $_.Name -notlike "_*" } |
  Where-Object { -not $Flow -or $_.Name -like "$Flow*" } |
  Sort-Object Name

if (-not $flows) { throw "No flows matched '$Flow'" }

$results = @()
foreach ($f in $flows) {
  Write-Host "`n=== $($f.Name) ===" -ForegroundColor Cyan
  & maestro.bat test @argList $f.Name 2>&1 |
    Select-String -NotMatch "WARNING: |sun.misc|protobuf|Debug tests faster|Maestro Cloud|maestro cloud|^\s*$" |
    ForEach-Object { Write-Host $_ }
  $ok = ($LASTEXITCODE -eq 0)
  $results += [pscustomobject]@{ Flow = $f.Name; Passed = $ok }
}

Write-Host "`n================ summary ================" -ForegroundColor Cyan
foreach ($r in $results) {
  $colour = if ($r.Passed) { "Green" } else { "Red" }
  $mark = if ($r.Passed) { "PASS" } else { "FAIL" }
  Write-Host ("  {0,-24} {1}" -f $r.Flow, $mark) -ForegroundColor $colour
}
$failed = @($results | Where-Object { -not $_.Passed }).Count
Write-Host "`n$($results.Count - $failed)/$($results.Count) passed. Screenshots in qa/maestro/artifacts/`n"
exit $failed
