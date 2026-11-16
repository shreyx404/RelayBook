@echo off
title MySQL Server 8.4 (Windows Native)
color 09
echo Starting MySQL Server 8.4 on localhost:3306...
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --datadir="%~dp0data" --console
