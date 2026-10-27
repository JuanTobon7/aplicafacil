@echo off
rem Atajo para dev-up.ps1 sin depender de la ExecutionPolicy (sirve con doble clic).
rem Acepta los mismos parametros: dev-up -Build, dev-up -Dev, dev-up -Down
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-up.ps1" %*
