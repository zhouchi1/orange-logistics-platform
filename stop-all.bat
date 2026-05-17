@echo off
chcp 65001 >nul
title 橙子便利物流平台 - 一键停止
color 0C

echo ╔══════════════════════════════════════════════════════╗
echo ║     橙子便利物流 - 停止所有服务                     ║
echo ╚══════════════════════════════════════════════════════╝
echo.

:: 停止 Java 服务
echo [1/3] 停止 Java 微服务...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr "8888 8081 8082 8083 8084 8085 8092 8093" ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F >nul 2>&1
)
echo [OK] Java 服务已停止

:: 停止 Python 服务
echo [2/3] 停止 Python AI 服务...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr "8001 8002" ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F >nul 2>&1
)
echo [OK] Python 服务已停止

:: 停止 Docker
echo [3/3] 停止 Docker 基础设施...
cd /d "%~dp0"
docker compose down
echo [OK] Docker 容器已停止

echo.
echo 所有服务已停止。
pause
