@echo off
title RelayBook - Live MySQL Tables
color 0B
echo.
echo ========================================================================
echo        RELAYBOOK LIVE MYSQL DATABASE TABLES (Native Windows 8.4)
echo ========================================================================
echo.
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -t -e "SELECT id, contact_type, full_name, preferred_channel, updated_at FROM contacts; SELECT contact_id, channel, value, last_verified, label FROM contact_methods;" relaybook
echo.
echo ========================================================================
echo  Type any SQL query below (e.g. SELECT * FROM contacts;):
echo ========================================================================
echo.
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root relaybook
pause
