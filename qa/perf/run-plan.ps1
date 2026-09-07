# Everything for one Hetzner plan: recreate the stack under its caps, run the ladder,
# then sweep upwards until it breaks.
#
#   .\run-plan.ps1 -Plan cx33
#   .\run-plan.ps1 -Plan cx23 -Seconds 90 -Sweep 4,8,16,24,32
#
# The stack is recreated between plans rather than reconfigured, because cpuset and
# mem_limit only take effect on container creation — changing the override and running
# `up -d` without --force-recreate silently keeps the old limits and produces three
# identical result sets.

param(
  [Parameter(Mandatory = $true)][ValidateSet("cax11", "cx23", "cx33", "uncapped")][string]$Plan,
  [int]$Seconds = 90,
  [int[]]$Dau = @(1000, 10000, 50000),
  [double[]]$Sweep = @(4, 8, 16, 24, 32, 48),
  [int]$SweepSeconds = 45,
  [string]$GeneratorCpus = "8-15"
)

# Continue, not Stop. `docker compose` writes its progress to stderr, and PowerShell wraps
# every native-command stderr line in an ErrorRecord — with Stop, the first "Container
# Recreate" line aborts the run before anything has actually gone wrong. Exit codes are
# checked explicitly instead.
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot
$root = Resolve-Path "$PSScriptRoot\..\.."

function Invoke-K6 {
  param([hashtable]$EnvVars)
  $args = @(
    "run", "--rm", "-i",
    "--network", "gamebuddy-perf_default",
    "--cpuset-cpus=$GeneratorCpus",
    "-v", "${PWD}:/scripts", "-w", "/scripts",
    "-e", "GB_BASE_URL=http://backend:8080"
  )
  foreach ($k in $EnvVars.Keys) { $args += @("-e", "$k=$($EnvVars[$k])") }
  $args += @("grafana/k6:latest", "run", "--quiet", "--no-usage-report", "s02-ladder.js")
  & docker @args
}

# --- bring the stack up under this plan's caps -----------------------------------------
Write-Host "`n================ $Plan ================" -ForegroundColor Cyan
Push-Location $root
node qa/perf/plan.js $Plan
docker compose -p gamebuddy-perf -f docker-compose.yml -f qa/perf/docker-compose.perf.yml -f qa/perf/plan.override.yml up -d --force-recreate postgres model redis backend *>$null
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "compose up failed for plan $Plan" }
Pop-Location

Write-Host "waiting for health..." -NoNewline
$deadline = (Get-Date).AddMinutes(5)
do {
  Start-Sleep -Seconds 5
  $state = docker inspect --format '{{.State.Health.Status}}' gamebuddy-perf-backend-1 2>$null
  Write-Host "." -NoNewline
} while ($state -ne "healthy" -and (Get-Date) -lt $deadline)
Write-Host " $state"
if ($state -ne "healthy") { throw "backend did not become healthy under plan $Plan" }

# A warm-up, discarded. The first requests after a restart pay for JIT compilation, an
# empty Hikari pool and a cold page cache, and folding that into the 1,000 DAU tier makes
# the smallest tier look like the worst one.
Write-Host "warm-up..." -ForegroundColor DarkGray
Invoke-K6 @{ GB_PLAN = "warmup"; GB_SESSIONS_PER_SEC = 3; GB_STAGE_SECONDS = 30; GB_LABEL = "warmup-discard" } *>$null
Start-Sleep -Seconds 10

# --- the ladder ------------------------------------------------------------------------
foreach ($d in $Dau) {
  Write-Host "`n--- $Plan : $d DAU ---" -ForegroundColor Yellow
  Invoke-K6 @{ GB_PLAN = $Plan; GB_DAU = $d; GB_STAGE_SECONDS = $Seconds }
  Start-Sleep -Seconds 10
}

# --- the sweep, to find the ceiling ----------------------------------------------------
Write-Host "`n--- $Plan : breakpoint sweep ---" -ForegroundColor Yellow
foreach ($r in $Sweep) {
  $label = "sweep-$Plan-$($r -replace '\.','p')"
  Write-Host "`n  $r sessions/s" -ForegroundColor DarkYellow
  Invoke-K6 @{ GB_PLAN = $Plan; GB_SESSIONS_PER_SEC = $r; GB_STAGE_SECONDS = $SweepSeconds; GB_LABEL = $label }
  Start-Sleep -Seconds 10
}

Write-Host "`n$Plan done. Results in qa/perf/results/" -ForegroundColor Green
