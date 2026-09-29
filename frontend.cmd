@echo off
title TourInvest - Sitio (puerto 80, abre el navegador)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" run-frontend
pause