# Test 7 (Bonus 1): the same failover for the course and auth databases.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T7 course and auth database failover"
Require-Stack
Test-DbFailover 'course'
Test-DbFailover 'auth'
Finish
