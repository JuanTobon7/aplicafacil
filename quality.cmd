@echo off
rem Atajo para quality.ps1 sin depender de la ExecutionPolicy. Acepta: -Fast, -Full, -Base origin/develop
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0quality.ps1" %*
