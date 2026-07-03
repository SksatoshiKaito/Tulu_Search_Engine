@echo off
title TULU Search Engine - Online Server
color 0A

echo.
echo  ========================================
echo     TULU Search Engine - Public Server
echo  ========================================
echo.

REM Step 1: Start Docker services (Elasticsearch + Redis)
echo [1/3] Starting Elasticsearch and Redis...
docker-compose -f "C:\Users\User\IdeaProjects\WebCrawler\docker-compose.yml" up -d
echo       Waiting for databases to be ready...
timeout /t 20 /nobreak > nul
echo       Done! Databases are running.
echo.

REM Step 2: Start the Java Search Engine in background
echo [2/3] Starting TULU Search Engine...
start "TULU Search Engine" cmd /k "cd /d C:\Users\User\IdeaProjects\WebCrawler && mvn exec:java -Dexec.mainClass=org.example.Main"
echo       Waiting for engine to start...
timeout /t 15 /nobreak > nul
echo       Done! Engine running on http://localhost:8082
echo.

REM Step 3: Start Cloudflare Tunnel
echo [3/3] Creating public internet link via Cloudflare...
echo.
echo  ============================================
echo   YOUR PUBLIC LINK WILL APPEAR BELOW:
echo   (Share this link with anyone in the world!)
echo  ============================================
echo.
"C:\Users\User\IdeaProjects\WebCrawler\cloudflared.exe" tunnel --url http://localhost:8082

echo.
echo  Server stopped. Press any key to exit.
pause
