# Test 6 (Bonus 1): grade database failover and failback.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T6 grade database failover"
Require-Stack
Test-DbFailover 'grade'
Finish
