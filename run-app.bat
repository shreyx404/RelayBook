@echo off
title RelayBook - Swing Desktop GUI and Web Server
cd /d "%~dp0"
set "DB_URL=jdbc:mysql://127.0.0.1:3306/relaybook?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
set "DB_USER=root"
set "DB_PASSWORD="
echo Connecting to native Windows MySQL database...
echo Starting RelayBook Swing Desktop GUI and Web Server...
java --add-modules jdk.httpserver -cp "target\classes;C:\Users\shrey\.m2\repository\com\mysql\mysql-connector-j\8.4.0\mysql-connector-j-8.4.0.jar" com.relaybook.App
pause
