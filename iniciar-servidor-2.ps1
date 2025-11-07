# Script PowerShell para iniciar Servidor 2

Write-Host "🚀 Iniciando Servidor 2..." -ForegroundColor Green
Write-Host "Puerto cliente: 8081" -ForegroundColor Yellow
Write-Host "Puerto P2P: 9091" -ForegroundColor Yellow
Write-Host "Base de datos: chat_servidor (compartida)" -ForegroundColor Yellow
Write-Host "Conecta con: Servidor 1 (localhost:9090)" -ForegroundColor Cyan
Write-Host ""

Set-Location c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run '-Dserver.port=8081' '-Dp2p.port=9091' '-Dp2p.peers=localhost:9090'
