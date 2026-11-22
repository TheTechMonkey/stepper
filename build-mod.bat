@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\gradlew.bat' build 2>&1 | Tee-Object -FilePath 'build-log.txt'; 'EXIT CODE: ' + $LASTEXITCODE | Tee-Object -FilePath 'build-log.txt' -Append"
pause
