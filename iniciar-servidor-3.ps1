# Script PowerShell para iniciar Servidor 3

Write-Host "🚀 Iniciando Servidor 3..." -ForegroundColor Green
Write-Host "Puerto cliente: 8082" -ForegroundColor Yellow
Write-Host "Puerto P2P: 9092" -ForegroundColor Yellow
Write-Host "Base de datos: chat_servidor (compartida)" -ForegroundColor Yellow
Write-Host "Conecta con: Servidor 1 (localhost:9090)" -ForegroundColor Cyan
Write-Host ""

Set-Location c:\Users\monto\universidad\Arqui\cliente-servidor\servidor

mvn javafx:run '-Dserver.port=8082' '-Dp2p.port=9092' '-Dp2p.peers=localhost:9090'
