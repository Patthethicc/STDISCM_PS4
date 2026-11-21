# Test 4: the grades spec: own grades only, faculty courses, batch validation, update existing.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T4 grades API"
Require-Stack
JavaCheck 'grades: own-only, faculty courses, validation, update' @('tests/e2e/ApiAndFailoverTest.java', 'grades', 'http://localhost')
Finish
