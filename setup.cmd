@echo off
rem TourInvest - lanzador universal del setup (funciona tambien con doble clic)
rem Uso: setup.cmd [check|seed|run|run-backend|run-backend-fast|run-frontend|help]
rem Ejemplo: setup.cmd run   -> arranca API (:8080) + sitio (puerto 80)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
if "%~1"=="" pause