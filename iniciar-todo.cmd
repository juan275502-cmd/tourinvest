@echo off
rem ============================================================
rem  TourInvest - ARRANQUE COMPLETO DEL PROYECTO (Windows)
rem  Delega en setup.ps1 run, que es el unico lanzador:
rem    1) API Spring Boot en http://www.tourinvest.com:8080  (logs\backend.log)
rem    2) Sitio estatico en http://www.tourinvest.com (logs\frontend.log)
rem       y abre el navegador automaticamente.
rem  Para DETENER: pulsa Ctrl+C en esta ventana o ciérrala
rem  (se apagan API y sitio).
rem ============================================================
title TourInvest - proyecto completo

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" run

echo.
echo   Para DETENER el proyecto: pulsa Ctrl+C aqui o cierra esta ventana.
pause
