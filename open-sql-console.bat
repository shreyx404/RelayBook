@echo off
title Live MySQL Console - RelayBook Database
color 0A
echo ======================================================================
echo          RELAYBOOK LIVE MYSQL DATABASE CONSOLE (Native Windows)
echo ======================================================================
echo  Server: localhost:3306 (MySQL Server 8.4)
echo  Database: relaybook
echo.
echo  Try running queries like:
echo    SELECT id, full_name, contact_type, preferred_channel FROM contacts;
echo    SELECT * FROM contact_methods;
echo    SELECT c.full_name, m.channel, m.value, m.last_verified FROM contacts c JOIN contact_methods m ON c.id=m.contact_id;
echo ======================================================================
echo.
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root relaybook
