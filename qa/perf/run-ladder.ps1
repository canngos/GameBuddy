# Runs the ladder for one plan: 1,000 / 10,000 / 50,000 DAU.
#
#   .\run-ladder.ps1 -Plan cx33 -Seconds 180
#
# k6 runs in a container on the cores the system under test does not have. Without the
# cpuset split the generator and the JVM contend for the same silicon and the result is a
# measurement of this laptop rather than of GameBuddy.

param(
  [Parameter(Mandatory = $true)][string]$Plan,
  [int]$Seconds = 180,
  [int[]]$Dau = @(1000, 10000, 50000),
  [string]$GeneratorCpus = "8-15"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "`n=== $Plan ===" -ForegroundColor Cyan

foreach ($d in $Dau) {
  Write-Host "`n--- $d DAU ---" -ForegroundColor Yellow

  docker run --rm -i `
    --network gamebuddy-perf_default `
    --cpuset-cpus="$GeneratorCpus" `
    -v "${PWD}:/scripts" -w /scripts `
    -e GB_BASE_URL=http://backend:8080 `
    -e GB_PLAN=$Plan `
    -e GB_DAU=$d `
    -e GB_STAGE_SECONDS=$Seconds `
    grafana/k6:latest run --quiet --no-usage-report s02-ladder.js

  # A gap between tiers so the JVM settles and the next tier is not measured on the tail
  # of this one.
  Start-Sleep -Seconds 20
}

Write-Host "`nResults in qa/perf/results/" -ForegroundColor Green
