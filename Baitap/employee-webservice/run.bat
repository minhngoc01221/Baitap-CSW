@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Vui long cai JDK 17 va them java vao PATH.
  pause
  exit /b 1
)
if not exist "target\employee-webservice.jar" (
  call mvn clean package
  if errorlevel 1 (
    echo Build that bai. Kiem tra JDK 17, Maven va ket noi Internet.
    pause
    exit /b 1
  )
)
echo Mo trinh duyet tai http://127.0.0.1:8080/
java -jar target\employee-webservice.jar
pause
