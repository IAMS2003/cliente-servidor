# Script para iniciar el servidor
Write-Host "Iniciando Servidor de Chat Universidad..." -ForegroundColor Green
mvn -f servidor/pom.xml exec:java
