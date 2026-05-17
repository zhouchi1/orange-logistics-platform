@echo off
chcp 65001 >nul
title 橙子便利物流平台 - 一键启动
color 0A

echo ╔══════════════════════════════════════════════════════╗
echo ║     橙子便利物流 - 智能实时监控平台                 ║
echo ║     Orange Logistics Platform - Full Stack          ║
echo ╚══════════════════════════════════════════════════════╝
echo.

set PROJECT_DIR=%~dp0
set JAVA_OPTS=-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Dspring.config.import=optional:nacos:%NACOS_ADDR%
set NACOS_ADDR=localhost:8848
set REDIS_HOST=localhost
set REDIS_PORT=6379
set MYSQL_PORT=13306
set KAFKA_SERVERS=localhost:29092

:: ============================================
:: Step 1: 启动 Docker 基础设施
:: ============================================
echo [1/4] 启动 Docker 基础设施...
echo.

docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 未运行！请先启动 Docker Desktop
    echo         等待 Docker Desktop 启动...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    :wait_docker
    timeout /t 5 /nobreak >nul
    docker info >nul 2>&1
    if errorlevel 1 goto wait_docker
    echo [OK] Docker Desktop 已就绪
)

cd /d "%PROJECT_DIR%"
docker compose up -d
echo.

:: 等待基础设施就绪
echo [INFO] 等待基础设施就绪...
:wait_infra
timeout /t 5 /nobreak >nul
docker inspect --format="{{.State.Health.Status}}" orange-mysql 2>nul | findstr "healthy" >nul
if errorlevel 1 (
    echo        等待 MySQL...
    goto wait_infra
)
docker inspect --format="{{.State.Health.Status}}" orange-redis 2>nul | findstr "healthy" >nul
if errorlevel 1 (
    echo        等待 Redis...
    goto wait_infra
)
echo [OK] MySQL + Redis + Kafka + ES + Nacos + Prometheus + Grafana 已就绪
echo.

:: 导入 Nacos 配置（如果为空）
powershell -Command "$r = Invoke-WebRequest -Uri 'http://localhost:8848/nacos/v1/cs/configs?dataId=common.yml&group=DEFAULT_GROUP' -UseBasicParsing -ErrorAction SilentlyContinue; if ($r.StatusCode -ne 200) { exit 1 }" >nul 2>&1
if errorlevel 1 (
    echo [INFO] 导入 Nacos 配置...
    powershell -ExecutionPolicy Bypass -File "%PROJECT_DIR%scripts\import-nacos-config.ps1"
    echo [OK] Nacos 配置导入完成
)

:: ============================================
:: Step 2: 启动 Java 微服务
:: ============================================
echo [2/4] 启动 Java 微服务...
echo.

:: Gateway (核心网关)
echo   启动 Gateway (8888)...
start "Gateway-8888" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-gateway && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -DREDIS_HOST=%REDIS_HOST% -jar target\orange-logistics-gateway-1.0.0-SNAPSHOT.jar"

:: Auth
echo   启动 Auth (8081)...
start "Auth-8081" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-auth && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-auth-1.0.0-SNAPSHOT.jar --server.port=8081"

:: Order
echo   启动 Order (8082)...
start "Order-8082" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-order && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-order-1.0.0-SNAPSHOT.jar --server.port=8082"

:: Waybill
echo   启动 Waybill (8083)...
start "Waybill-8083" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-waybill && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-waybill-1.0.0-SNAPSHOT.jar --server.port=8083"

:: Transport
echo   启动 Transport (8084)...
start "Transport-8084" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-transport && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-transport-1.0.0-SNAPSHOT.jar --server.port=8084"

:: Dispatch
echo   启动 Dispatch (8085)...
start "Dispatch-8085" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-dispatch && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-dispatch-1.0.0-SNAPSHOT.jar --server.port=8085"

:: Config
echo   启动 Config (8092)...
start "Config-8092" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-config && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-config-1.0.0-SNAPSHOT.jar"

:: IM
echo   启动 IM (8093)...
start "IM-8093" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-im && java %JAVA_OPTS% -DNACOS_ADDR=%NACOS_ADDR% -jar target\orange-logistics-im-1.0.0-SNAPSHOT.jar --server.port=8093"

echo [OK] Java 微服务启动中...
echo.

:: ============================================
:: Step 3: 启动 Python AI 服务
:: ============================================
echo [3/4] 启动 Python AI 服务...
echo.

:: AI Prediction
echo   启动 AI Prediction (8001)...
start "AI-Prediction-8001" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-ai-prediction && venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8001"

:: AI Agent
echo   启动 AI Agent (8002)...
start "AI-Agent-8002" /min cmd /c "cd /d %PROJECT_DIR%orange-logistics-ai-agent && venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8002"

echo [OK] Python AI 服务启动中...
echo.

:: ============================================
:: Step 4: 验证
:: ============================================
echo [4/4] 等待服务就绪 (约30秒)...
timeout /t 30 /nobreak >nul

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                 服务启动完成                         ║
echo ╠══════════════════════════════════════════════════════╣
echo ║  基础设施:                                          ║
echo ║    Nacos Console    http://localhost:8848/nacos      ║
echo ║    Grafana          http://localhost:3000            ║
echo ║    Prometheus       http://localhost:9090            ║
echo ║    Elasticsearch    http://localhost:9200            ║
echo ║    MySQL            localhost:13306                  ║
echo ║    Redis            localhost:6379                   ║
echo ║    Kafka            localhost:29092                  ║
echo ║                                                      ║
echo ║  微服务:                                            ║
echo ║    API Gateway      http://localhost:8888            ║
echo ║    Auth Service     http://localhost:8081            ║
echo ║    Order Service    http://localhost:8082            ║
echo ║    Waybill Service  http://localhost:8083            ║
echo ║    Transport        http://localhost:8084            ║
echo ║    Dispatch         http://localhost:8085            ║
echo ║    Config           http://localhost:8092            ║
echo ║    IM WebSocket     http://localhost:8093            ║
echo ║                                                      ║
echo ║  AI 服务:                                           ║
echo ║    AI Prediction    http://localhost:8001            ║
echo ║    AI Agent         http://localhost:8002            ║
echo ║                                                      ║
echo ║  账号:                                              ║
echo ║    Nacos: nacos / nacos                             ║
echo ║    Grafana: admin / orange2024                      ║
echo ║    MySQL: root / orange_logistics_2024              ║
echo ╚══════════════════════════════════════════════════════╝
echo.
echo 按任意键关闭此窗口（服务继续在后台运行）...
pause >nul
