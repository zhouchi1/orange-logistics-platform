@echo off
chcp 65001 >nul
echo ========================================
echo   橙子便利物流 - AI Agent 服务
echo   Port: 8002
echo ========================================

cd /d "%~dp0"

REM 检查虚拟环境
if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
    echo [OK] 虚拟环境已激活
) else (
    echo [WARN] 未找到虚拟环境，使用系统 Python
)

REM 检查依赖
python -c "import fastapi" 2>nul
if errorlevel 1 (
    echo [INFO] 安装依赖...
    pip install -r requirements.txt
)

echo.
echo [START] 启动 AI Agent 服务...
python -m uvicorn app.main:app --host 0.0.0.0 --port 8002 --reload

pause
