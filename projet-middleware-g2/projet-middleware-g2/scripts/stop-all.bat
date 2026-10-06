@echo off
echo === Arret du projet Middleware G2 ===
docker stop notification-consumer tomcat-rdv keycloak rabbitmq postgres
docker ps
