@echo off
echo === Demarrage du projet Middleware G2 ===
echo (Docker Desktop doit etre lance : "Engine running")
docker start postgres rabbitmq keycloak
echo Attente du demarrage de Keycloak (40 secondes)...
timeout /t 40 /nobreak >nul
docker start tomcat-rdv notification-consumer
echo.
docker ps
echo.
echo Console RabbitMQ : http://localhost:15672  (admin / admin123)
echo Console Keycloak : http://localhost:8081   (admin / admin123)
echo Test Tomcat      : http://localhost:8080/health
