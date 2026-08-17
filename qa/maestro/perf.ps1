# UI performance measurement, on top of the functional flows.
#
#   .\perf.ps1 -Label baseline -ReleaseBuild        # all flows, 3 iterations each
#   .\perf.ps1 -Label phase2 -Flow 03 -Iterations 5 # one flow, more samples
#   .\perf.ps1 -Compare baseline,phase2             # print the delta table, run nothing
#
# A sibling of `run.ps1` rather than a change to it. `run.ps1` is the functional gate and
# wants to stay boring; this wraps it with adb so the same seven journeys also produce
# numbers. It shells out to `run.ps1 -SkipProvision` per flow, so provisioning happens
# once here and the flows themselves are untouched.
#
# **Run it against a release build.** A debug build keeps React Native's dev machinery
# attached and runs its bundle from Metro; its frame times are several times worse and not
# in proportion, so a debug baseline would rank the work wrongly. `-ReleaseBuild` is passed
# straight through to `run.ps1`, which already knows what it means.
#
# What is measured, and why these four:
#
#   * 99th-percentile frame time  -- the stall a person actually notices.
#   * janky frame %               -- how often it is not smooth, rather than how badly.
#   * missed vsync                -- frames the compositor never got at all.
#   * cold start (am start -W)    -- time to the first window, which no frame metric sees.
#
# One caveat worth knowing before reading any number: `gfxinfo` only counts frames the app
# *drew*. A hard JavaScript block produces no frames at all, so it shows up as a suspiciously
# low frame count with flattering percentiles rather than as a bad percentile. Always read
# `Frames` next to `p99`; a flow whose frame count collapses got worse, not better.
#
# And Maestro's input is synthetic -- no fling velocity, no human dwell. It is *repeatable*,
# which is the only property a before/after needs. Do not quote the absolute jank% as a
# claim about real users.

param(
  # Names the run. Becomes the CSV filename, so use something you will recognise later.
  [string]$Label = "baseline",
  [string]$Flow = "",
  # Median of N — but **1 is the honest default for this suite**, because the flows are
  # not idempotent.
  #
  # `run.ps1` provisions once and runs each flow once, and several flows consume the state
  # they were given: 01-onboarding verifies its pending account, so a second pass signs in
  # to an already-verified user and fails at "Account not verified"; 03-chat sends a reply,
  # so the seeded last message it asserts on is no longer the last message. Measured, not
  # assumed — both failed exactly that way on iteration 2.
  #
  # Raising this only makes sense for the stateless flows (02, 04, 05), or alongside a
  # re-provision per iteration, which this script deliberately does not do because
  # provisioning is the slowest part of a run.
  [int]$Iterations = 1,
  [switch]$ReleaseBuild,
  # Add to an existing label's CSV instead of replacing it.
  #
  # A full seven-flow run takes a while and anything can interrupt it — a killed shell, a
  # dropped emulator — so being able to measure a few flows, come back, and add the rest to
  # the same label is what makes a long run survivable. Without it each invocation clobbers
  # the last and a partial run is worth nothing.
  [switch]$Append,
  # Two labels. Prints the delta and exits without measuring anything.
  #
  # `[string[]]` rather than `[string]`: PowerShell turns `-Compare a,b` into an array at
  # the call site, and a `[string]` parameter would silently flatten it to "a b" -- which
  # then splits on ',' into one element and reports "takes exactly two labels" about an
  # invocation that named exactly two.
  [string[]]$Compare = @()
)

$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

# Numbers are formatted and parsed in one culture, and it is not the machine's.
#
# This shell runs as tr-TR, where the decimal separator is a comma. `Export-Csv` wrote a
# jank figure of 17.43 as "17,43", and reading it back with `[double]` — which parses
# invariantly — took the comma for a thousands separator and returned **1743**. The CSV
# looked fine, the console looked fine, and only the summary table was wrong, by a factor
# of a hundred. Pinning the culture makes writing and reading agree on the dot.
[System.Threading.Thread]::CurrentThread.CurrentCulture = [System.Globalization.CultureInfo]::InvariantCulture

$env:PATH = "C:\Users\canba\.maestro\bin;C:\Users\canba\AppData\Local\Android\Sdk\platform-tools;" + $env:PATH

# provision.js resolves the backend through `GB_BASE_URL`, defaulting to
# `http://localhost:8080` (qa/functional/helpers/api.js). On this machine that default does
# not work: `localhost` resolves to ::1 first, nothing is listening there, and the request
# hangs until it times out -- so provisioning fails with a bare "fetch failed" and the
# harness stops before it has measured anything. `127.0.0.1` is answered immediately.
# Set here rather than left to the shell so a fresh terminal cannot reintroduce it.
if (-not $env:GB_BASE_URL) { $env:GB_BASE_URL = "http://127.0.0.1:8080" }

$pkg = "com.findgamebuddy.app"
$outDir = Join-Path $PSScriptRoot "artifacts\perf"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# --- comparison mode ------------------------------------------------------------------
# Reads two CSVs and prints one table. Deliberately does not re-run anything: comparing is
# something you do repeatedly while reading, and it should not cost forty minutes.

function Get-Median {
  param([double[]]$Values)
  if (-not $Values -or $Values.Count -eq 0) { return $null }
  $sorted = $Values | Sort-Object
  $mid = [int][Math]::Floor($sorted.Count / 2)
  if ($sorted.Count % 2 -eq 1) { return $sorted[$mid] }
  return ($sorted[$mid - 1] + $sorted[$mid]) / 2
}

function Read-Run {
  param([string]$RunLabel)
  $path = Join-Path $outDir "$RunLabel.csv"
  if (-not (Test-Path $path)) { throw "No results for '$RunLabel' at $path" }

  $rows = Import-Csv $path
  $byFlow = @{}
  foreach ($group in ($rows | Group-Object Flow)) {
    $byFlow[$group.Name] = [pscustomobject]@{
      P99      = Get-Median ($group.Group | ForEach-Object { [double]$_.P99 })
      P95      = Get-Median ($group.Group | ForEach-Object { [double]$_.P95 })
      JankPct  = Get-Median ($group.Group | ForEach-Object { [double]$_.JankPct })
      Vsync    = Get-Median ($group.Group | ForEach-Object { [double]$_.MissedVsync })
      Frames   = Get-Median ($group.Group | ForEach-Object { [double]$_.Frames })
      ColdMs   = Get-Median ($group.Group | ForEach-Object { [double]$_.ColdStartMs })
    }
  }
  return $byFlow
}

if ($Compare.Count -gt 0) {
  # Accepts both `-Compare a,b` (already an array) and `-Compare "a,b"` (one string).
  $labels = @($Compare | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
  if ($labels.Count -ne 2) { throw "-Compare takes exactly two labels, e.g. -Compare baseline,phase2" }

  $before = Read-Run $labels[0].Trim()
  $after = Read-Run $labels[1].Trim()

  Write-Host "`n| flow | p99 ms | jank % | vsync | frames | cold ms |" -ForegroundColor Cyan
  Write-Host "| --- | --- | --- | --- | --- | --- |"
  foreach ($flow in ($before.Keys | Sort-Object)) {
    if (-not $after.ContainsKey($flow)) { continue }
    $b = $before[$flow]; $a = $after[$flow]
    $cell = {
      param($x, $y, $unit)
      if ($null -eq $x -or $null -eq $y) { return "-" }
      "{0:N0}{2} -> {1:N0}{2}" -f $x, $y, $unit
    }
    Write-Host ("| {0} | {1} | {2} | {3} | {4} | {5} |" -f `
      $flow,
      (& $cell $b.P99 $a.P99 ""),
      (& $cell $b.JankPct $a.JankPct "%"),
      (& $cell $b.Vsync $a.Vsync ""),
      (& $cell $b.Frames $a.Frames ""),
      (& $cell $b.ColdMs $a.ColdMs ""))
  }
  Write-Host "`nframes falling sharply is a warning, not a win -- see the header note.`n" -ForegroundColor DarkGray
  exit 0
}

# --- measurement mode -----------------------------------------------------------------

$devices = & adb devices | Select-String "emulator|device$" | Where-Object { $_ -notmatch "List of" }
if (-not $devices) { throw "No device. Start the emulator: emulator -avd GameBuddy_API34" }

# Provision once, here, so the per-flow `run.ps1` calls can all skip it. Without this every
# iteration would re-provision accounts and the timings would include that work.
Push-Location (Resolve-Path "$PSScriptRoot\..\..")
$provArgs = (& node qa/maestro/provision.js --args) -join " "
Pop-Location
if (-not $provArgs) { throw "provision.js produced nothing" }
Set-Content -Path (Join-Path $PSScriptRoot ".env-args") -Value $provArgs -Encoding utf8

$flows = Get-ChildItem -Path $PSScriptRoot -Filter "*.yaml" |
  Where-Object { $_.Name -notlike "_*" } |
  Where-Object { -not $Flow -or $_.Name -like "$Flow*" } |
  Sort-Object Name
if (-not $flows) { throw "No flows matched '$Flow'" }

function Get-GfxInfo {
  # Reset and read are two separate adb calls on purpose: resetting *after* the app is
  # running but *before* the flow starts is what keeps launch frames out of the numbers.
  $raw = (& adb shell dumpsys gfxinfo $pkg) -join "`n"

  # Single-quoted: these are regexes, and a double-quoted string would have PowerShell
  # trying to read the escapes and the bracket groups before the regex engine ever sees them.
  $frames = if ($raw -match 'Total frames rendered:\s*(\d+)') { [int]$Matches[1] } else { 0 }
  $jank = if ($raw -match 'Janky frames:\s*\d+\s*\(([\d.]+)%\)') { [double]$Matches[1] } else { 0 }
  $p95 = if ($raw -match '95th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { 0 }
  $p99 = if ($raw -match '99th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { 0 }
  $vsync = if ($raw -match 'Number Missed Vsync:\s*(\d+)') { [int]$Matches[1] } else { 0 }

  [pscustomobject]@{ Frames = $frames; JankPct = $jank; P95 = $p95; P99 = $p99; MissedVsync = $vsync }
}

$csvPath = Join-Path $outDir "$Label.csv"
$rows = @()

foreach ($f in $flows) {
  for ($i = 1; $i -le $Iterations; $i++) {
    Write-Host "`n=== $($f.Name)  iteration $i/$Iterations ===" -ForegroundColor Cyan

    # force-stop, not `pm clear`: clearing wipes SecureStore, which changes the code path
    # under test -- a cleared app signs in from scratch and never exercises session restore.
    & adb shell am force-stop $pkg | Out-Null
    Start-Sleep -Seconds 2

    $startRaw = (& adb shell am start -W -n "$pkg/.MainActivity") -join "`n"
    $coldMs = if ($startRaw -match 'TotalTime:\s*(\d+)') { [int]$Matches[1] } else { 0 }

    # Let the JS bundle finish evaluating before the counters are zeroed, or the first
    # flow's numbers carry the whole startup in them.
    Start-Sleep -Seconds 8
    & adb shell dumpsys gfxinfo $pkg reset | Out-Null

    # A hashtable, not an array. Splatting an *array* passes its elements positionally, so
    # `@("-Flow", "02", "-SkipProvision")` binds the literal string "-Flow" to $Flow and
    # drops both switches on the floor -- which showed up as run.ps1 demanding Metro during
    # a release run, because it never saw `-ReleaseBuild`. Hashtable splatting binds by name.
    $runArgs = @{ Flow = $f.Name.Substring(0, 2); SkipProvision = $true }
    if ($ReleaseBuild) { $runArgs['ReleaseBuild'] = $true }
    & (Join-Path $PSScriptRoot "run.ps1") @runArgs | Out-Null
    $passed = ($LASTEXITCODE -eq 0)

    $gfx = Get-GfxInfo
    $mem = (& adb shell dumpsys meminfo $pkg) -join "`n"
    $pss = if ($mem -match 'TOTAL PSS:\s*(\d+)') { [int]$Matches[1] } else { 0 }

    $rows += [pscustomobject]@{
      Label = $Label; Flow = $f.Name; Iteration = $i; Passed = $passed
      Frames = $gfx.Frames; JankPct = $gfx.JankPct; P95 = $gfx.P95; P99 = $gfx.P99
      MissedVsync = $gfx.MissedVsync; ColdStartMs = $coldMs; TotalPssKb = $pss
    }

    $verdict = if ($passed) { "PASS" } else { "FAIL" }
    $line = "  p99 {0}ms | jank {1}% | vsync {2} | frames {3} | cold {4}ms | {5}" -f $gfx.P99, $gfx.JankPct, $gfx.MissedVsync, $gfx.Frames, $coldMs, $verdict
    Write-Host $line -ForegroundColor DarkGray
  }
}

if ($Append -and (Test-Path $csvPath)) {
  $rows | Export-Csv -Path $csvPath -NoTypeInformation -Encoding utf8 -Append
  # Re-read so the summary below reports the whole label, not just this invocation.
  $rows = Import-Csv $csvPath
} else {
  $rows | Export-Csv -Path $csvPath -NoTypeInformation -Encoding utf8
}

Write-Host "`n================ $Label (median of $Iterations) ================" -ForegroundColor Cyan
Write-Host "| flow | p99 ms | jank % | vsync | frames | cold ms | PSS MB |"
Write-Host "| --- | --- | --- | --- | --- | --- | --- |"
foreach ($group in ($rows | Group-Object Flow | Sort-Object Name)) {
  Write-Host ("| {0} | {1:N0} | {2:N1} | {3:N0} | {4:N0} | {5:N0} | {6:N0} |" -f `
    $group.Name,
    (Get-Median ($group.Group | ForEach-Object { [double]$_.P99 })),
    (Get-Median ($group.Group | ForEach-Object { [double]$_.JankPct })),
    (Get-Median ($group.Group | ForEach-Object { [double]$_.MissedVsync })),
    (Get-Median ($group.Group | ForEach-Object { [double]$_.Frames })),
    (Get-Median ($group.Group | ForEach-Object { [double]$_.ColdStartMs })),
    (Get-Median ($group.Group | ForEach-Object { [double]$_.TotalPssKb / 1024 })))
}

$failed = @($rows | Where-Object { -not $_.Passed }).Count
if ($failed -gt 0) {
  Write-Host "`n$failed iteration(s) FAILED functionally -- those numbers describe a broken run." -ForegroundColor Red
}
Write-Host "`nwritten to $csvPath"
Write-Host "compare with:  .\perf.ps1 -Compare OTHER_LABEL,$Label`n"
