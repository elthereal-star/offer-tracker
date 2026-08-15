@echo off
taskkill /IM OfferTracker.exe /F >nul 2>nul
if errorlevel 1 (
  echo Offer Tracker is not running.
) else (
  echo Offer Tracker has stopped.
)
timeout /t 2 /nobreak >nul
