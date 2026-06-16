param([switch]$RebuildLobby)

Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Users\anton\AppData\Local\Programs\IntelliJ IDEA\jbr' }

function Step($m)  { Write-Host "==> $m" -ForegroundColor Cyan }
function Check($m) { if ($LASTEXITCODE -ne 0) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 } }

$lobby = 'ghcr.io/antndev/nebula-lobby:latest'

Step 'stopping daemon + managed containers'
docker rm -f nebula-daemon 2>$null | Out-Null
$managed = docker ps -aq --filter 'label=nebula.managed=true'
if ($managed) { docker rm -f $managed | Out-Null }

if ($RebuildLobby -or -not (docker images -q $lobby)) {
    Step 'building lobby image (local, slow)'
    docker build -f services/lobby/Dockerfile -t $lobby .
    Check 'lobby image build'
} else {
    Write-Host '==> reusing local lobby image (pass -RebuildLobby to force)' -ForegroundColor DarkGray
}

Step 'building daemon dist'
& "$PSScriptRoot\gradlew.bat" :nebula-daemon:installDist --no-daemon --console=plain
Check 'gradle installDist'

Step 'building daemon image'
docker build -f nebula-daemon/Dockerfile -t nebula-daemon:latest .
Check 'daemon image build'

Step 'starting daemon'
docker run -d --name nebula-daemon -p 25565:25565 -p 7654:7654 -v /var/run/docker.sock:/var/run/docker.sock nebula-daemon:latest | Out-Null
Check 'docker run'

Write-Host '==> daemon up -> join at localhost:25565   (Ctrl+C ends log view, daemon keeps running)' -ForegroundColor Green
docker logs -f nebula-daemon
