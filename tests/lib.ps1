# Shared helpers for the tests/t*.ps1 scripts. Dot-sourced: . "$PSScriptRoot/lib.ps1"
# Nothing here runs git. Needs PowerShell 7, Docker Desktop and Java 21 on PATH.
$ErrorActionPreference = 'Continue'
$global:Root   = Split-Path $PSScriptRoot -Parent
$global:Deploy = Join-Path $global:Root 'deploy'
$global:Fails  = 0
Set-Location $global:Root   # the Java tests use paths relative to the repo root

# docker compose for the whole stack (replicas included), usable from any folder
function dc { docker compose --project-directory $global:Deploy -f "$global:Deploy/docker-compose.yml" -f "$global:Deploy/docker-compose.replica.yml" --profile all @args }

function Check([string]$Name, [bool]$Ok, [string]$Detail = '') {
    if ($Ok) { Write-Host "PASS  $Name" -ForegroundColor Green }
    else { Write-Host "FAIL  $Name $Detail" -ForegroundColor Red; $global:Fails++ }
}

function Finish {
    if ($global:Fails -eq 0) { Write-Host "`nRESULT: PASS" -ForegroundColor Green; exit 0 }
    Write-Host "`nRESULT: FAIL ($($global:Fails) check(s) failed)" -ForegroundColor Red; exit 1
}

# Runs `java <args>` (named Run-Java because PowerShell names are case-insensitive: a function called Java would hide java.exe), shows its output, returns the exit code (0 = all its checks passed).
function Run-Java([string[]]$A) { java @A | Out-Host; return $LASTEXITCODE }
function JavaCheck([string]$Name, [string[]]$A) { Check $Name ((Run-Java $A) -eq 0) 'see the output above' }

# Waits until the website answers a login, then lets the other nodes settle.
function Wait-Stack([int]$Seconds = 180, [int]$Settle = 15) {
    $end = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $end) {
        try {
            $r = Invoke-WebRequest -Uri 'http://localhost/api/auth/login' -Method Post -ContentType 'application/json' `
                -Body '{"username":"alice","password":"password123"}' -SkipHttpErrorCheck -TimeoutSec 5
            if ($r.StatusCode -eq 200) { Start-Sleep $Settle; return $true }
        } catch { }
        Start-Sleep 3
    }
    return $false
}

function Require-Stack {
    if (-not (Wait-Stack 60 0)) { Check 'stack is up on http://localhost (run: dc up -d --build)' $false; Finish }
}

function Get-Since { (Get-Date).ToUniversalTime().AddSeconds(-5).ToString('yyyy-MM-ddTHH:mm:ssZ') }
function Logs([string]$Svc, [string]$Since = '24h') { (dc logs --since $Since $Svc 2>&1 | Out-String) }
function Psql([string]$Svc, [string]$User, [string]$Db, [string]$Sql) { (dc exec -T $Svc psql -U $User -d $Db -tAc $Sql 2>&1 | Out-String).Trim() }

# Stops one service's primary database, checks reads still work and writes are refused,
# starts it again, checks writes work, and checks the FAILOVER / FAILBACK log lines.
function Test-DbFailover([string]$N) {
    $db = "$N-db"; $svc = "$N-service"; $name = "${N}_db"
    $tbl = @{ grade = 'grades'; course = 'enrollment'; auth = 'users' }[$N]
    $caught = $false; $a = ''; $b = ''
    for ($i = 0; $i -lt 15 -and -not $caught; $i++) {
        $a = Psql $db $N $name "select count(*) from $tbl"
        $b = Psql "$db-replica" $N $name "select count(*) from $tbl"
        $caught = ($a -match '^\d+$' -and $a -eq $b)
        if (-not $caught) { Start-Sleep 2 }
    }
    Check "$N replica has the same rows as the primary ($a vs $b)" $caught
    $since = Get-Since
    try {
        dc stop $db | Out-Host
        Start-Sleep 3
        JavaCheck "$N-db DOWN: reads still served, writes refused" @('tests/e2e/ApiAndFailoverTest.java', 'db-down', 'http://localhost', $N)
        Check "$svc logged FAILOVER" ((Logs $svc $since) -match 'FAILOVER') 'no FAILOVER line in the service log'
    } finally {
        dc start $db | Out-Host
    }
    Start-Sleep 20
    JavaCheck "$N-db BACK: writes work again" @('tests/e2e/ApiAndFailoverTest.java', 'db-up', 'http://localhost', $N)
    Check "$svc logged FAILBACK" ((Logs $svc $since) -match 'FAILBACK') 'no FAILBACK line in the service log'
}
