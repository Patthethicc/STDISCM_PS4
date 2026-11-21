# Test 8: 20 students race for the 5 free slots of STRESS01. WIPES ALL DATA (down -v) to reseed.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T8 concurrent enrollment (resets all data first)"
dc down -v --remove-orphans | Out-Host
dc up -d --build | Out-Host
Check 'stack is up after the reset' (Wait-Stack 300 30)
$since = Get-Since
JavaCheck 'STRESS01: exactly 5 enrolled, no overbooking' @('tests/e2e/ConcurrentEnrollTest.java', 'STRESS01', 'http://localhost')
$log = Logs 'course-service' $since
Check 'no failover triggered by the load' (-not ($log -match 'FAILOVER')) 'the 500 ms pool wait looked like an outage under load'
$max = ([regex]::Matches($log, 'inUse=(\d+)') | ForEach-Object { [int]$_.Groups[1].Value } | Measure-Object -Maximum).Maximum
Check "pool never above its max of 10 (highest inUse seen: $max)" ($null -eq $max -or $max -le 10)
Finish
