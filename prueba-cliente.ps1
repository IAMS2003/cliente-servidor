# Script para ejecutar la prueba del cliente
Write-Host "Ejecutando prueba del protocolo TCP-IP..." -ForegroundColor Green
mvn -f cliente/pom.xml exec:java -Dexec.mainClass="com.universidad.chat.cliente.app.PruebaProtocolo"
