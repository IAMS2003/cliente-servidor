# Script para encontrar y verificar la base de datos H2 del cliente
Write-Host "=== Buscador de BD H2 del Cliente ===" -ForegroundColor Cyan
Write-Host ""

# Buscar archivos de BD H2
Write-Host "Buscando archivos de base de datos..." -ForegroundColor Yellow
$dbFiles = Get-ChildItem -Recurse -Filter "chat_cliente.mv.db" -ErrorAction SilentlyContinue

if ($dbFiles.Count -eq 0) {
    Write-Host "No se encontró ninguna base de datos." -ForegroundColor Red
    Write-Host ""
    Write-Host "La base de datos se creará la primera vez que ejecutes el cliente." -ForegroundColor Yellow
    Write-Host "Ubicación esperada (si ejecutas desde 'cliente/'):" -ForegroundColor Yellow
    Write-Host "  $(Join-Path $PWD 'data\chat_cliente.mv.db')" -ForegroundColor Gray
} else {
    Write-Host "Base(s) de datos encontrada(s):" -ForegroundColor Green
    Write-Host ""
    
    foreach ($file in $dbFiles) {
        Write-Host "📁 Ubicación: $($file.FullName)" -ForegroundColor Green
        Write-Host "   Tamaño: $([math]::Round($file.Length / 1KB, 2)) KB" -ForegroundColor Gray
        Write-Host "   Última modificación: $($file.LastWriteTime)" -ForegroundColor Gray
        Write-Host ""
    }
}

Write-Host ""
Write-Host "=== Información de configuración ===" -ForegroundColor Cyan
$configFile = "src\main\resources\config.properties"
if (Test-Path $configFile) {
    Write-Host "Leyendo $configFile..." -ForegroundColor Yellow
    $config = Get-Content $configFile | Where-Object { $_ -match "jdbc.url" }
    Write-Host "  $config" -ForegroundColor Gray
} else {
    Write-Host "Archivo config.properties no encontrado en ruta esperada." -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Comandos útiles ===" -ForegroundColor Cyan
Write-Host "Para abrir la consola web de H2 y explorar la BD:" -ForegroundColor Yellow
Write-Host "  java -cp `"target/classes;%USERPROFILE%\.m2\repository\com\h2database\h2\2.1.214\h2-2.1.214.jar`" org.h2.tools.Server -web -webPort 8082" -ForegroundColor Gray
Write-Host ""
Write-Host "Para borrar la BD y empezar de cero:" -ForegroundColor Yellow
Write-Host "  Remove-Item -Recurse -Force data" -ForegroundColor Gray
