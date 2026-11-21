# Test 5: stop each service node in turn; the rest of the site keeps working; restart; all works again.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T5 node-down modes"
Require-Stack
foreach ($n in 'grade', 'course', 'auth') {
    Write-Host "-- $n node"
    java tests/e2e/FailureTests.java login http://localhost | Out-Null   # fresh sessions (they last 15 min)
    try {
        dc stop "$n-service" | Out-Host
        Start-Sleep 3
        JavaCheck "$n node DOWN: the other nodes keep working" @('tests/e2e/FailureTests.java', "$n-down", 'http://localhost')
    } finally {
        dc start "$n-service" | Out-Host
    }
    Start-Sleep 25
    JavaCheck "$n node restarted: everything works again" @('tests/e2e/FailureTests.java', 'recovered', 'http://localhost')
}
Finish
