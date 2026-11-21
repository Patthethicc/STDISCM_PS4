# Runs every test script and prints one pass/fail table. From the repo root:
#   pwsh tests/run-all.ps1                    all tests (about 20 min; t08 and t09 wipe the data)
#   pwsh tests/run-all.ps1 -Only 3,4          just tests 3 and 4
#   pwsh tests/run-all.ps1 -SkipRegression    everything except t09
# The full output is also saved to a log file; its path is printed at the end.
param([int[]]$Only, [switch]$SkipRegression)
$log = Join-Path $env:TEMP 'stdiscm-tests.log'
Start-Transcript -Path $log -Force | Out-Null
$rows = @(); $failures = @()
foreach ($s in (Get-ChildItem $PSScriptRoot -Filter 't*.ps1' | Where-Object Name -match '^t\d\d-' | Sort-Object Name)) {
    $n = [int]$s.Name.Substring(1, 2)
    if ($Only -and $n -notin $Only) { continue }
    if ($SkipRegression -and $n -eq 9) { continue }
    Write-Host "`n############ $($s.Name) ############" -ForegroundColor Cyan
    & pwsh -NoProfile -File $s.FullName | Tee-Object -Variable out | Out-Host
    $code = $LASTEXITCODE
    $pass = @($out | Where-Object { "$_" -match '^PASS ' }).Count
    $fail = @($out | Where-Object { "$_" -match '^FAIL ' })
    $failures += $fail | ForEach-Object { "$($s.BaseName): $_" }
    $rows += [pscustomobject]@{ Test = $s.BaseName; Result = $(if ($code -eq 0) { 'PASS' } else { 'FAIL' }); Passed = $pass; Failed = $fail.Count }
}
Write-Host "`n============ SUMMARY ============"
$rows | Format-Table -AutoSize | Out-Host
if ($failures) { Write-Host 'Failed checks:' -ForegroundColor Red; $failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red } }
$failed = @($rows | Where-Object Result -eq 'FAIL').Count
if ($rows.Count -eq 0) { Write-Host 'NO TESTS RAN (nothing matched -Only, or no t*.ps1 scripts found)' -ForegroundColor Red; Stop-Transcript | Out-Null; exit 1 }
Write-Host $(if ($failed -eq 0) { 'ALL TESTS PASSED' } else { "$failed TEST SCRIPT(S) FAILED: send me $log" }) -ForegroundColor $(if ($failed -eq 0) { 'Green' } else { 'Red' })
Stop-Transcript | Out-Null
exit ([int]($failed -gt 0))
