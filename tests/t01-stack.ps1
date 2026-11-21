# Test 1: all containers up and healthy, website answers, each service uses the shared pool.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T1 stack is up and the pools are wired"
$rows = @(dc ps --format json | ForEach-Object { $_ | ConvertFrom-Json } | ForEach-Object { $_ })
Check "14 containers are running ($($rows.Count) found)" ($rows.Count -eq 14)
$bad = @($rows | Where-Object { $_.State -ne 'running' -or ($_.Health -and $_.Health -ne 'healthy') })
Check 'every container is running and healthy' ($bad.Count -eq 0) (($bad | ForEach-Object { "$($_.Service)=$($_.State)/$($_.Health)" }) -join ', ')
Check 'website answers on http://localhost' (Wait-Stack 30 0)
foreach ($s in 'auth', 'course', 'grade') {
    Check "$s-service uses the shared pool ($s-pool-primary)" ((Logs "$s-service") -match "$s-pool-primary")
}
Check 'auth-service has its replica pool too (auth-pool-replica)' ((Logs 'auth-service') -match 'auth-pool-replica')
Finish
