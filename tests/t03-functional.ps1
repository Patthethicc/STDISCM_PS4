# Test 3: login, enroll, FULL/CLOSED errors, grade upload, permissions, logout.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T3 functional end to end"
Require-Stack
JavaCheck 'functional (16 checks)' @('tests/e2e/FailureTests.java', 'functional', 'http://localhost')
Finish
