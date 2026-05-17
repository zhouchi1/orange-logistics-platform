@echo off
REM ============================================================
REM KubeSphere + GitLab 一键安装脚本
REM 等网络好的时候执行: install-kubesphere.bat
REM ============================================================

echo [1/4] 安装 KubeSphere Core...
helm upgrade --install -n kubesphere-system --create-namespace ks-core https://charts.kubesphere.io/main/ks-core-1.1.3.tgz --set global.imageRegistry=swr.cn-south-1.myhuaweicloud.com/ks --wait --timeout 15m

echo.
echo [2/4] 等待 KubeSphere 就绪...
kubectl -n kubesphere-system wait --for=condition=Available deployment/ks-core --timeout=600s

echo.
echo [3/4] 安装 DevOps 扩展（内置 Jenkins）...
REM 登录 KubeSphere 后在扩展市场安装 DevOps 组件

echo.
echo [4/4] 安装完成!
echo.
echo ========================================
echo   KubeSphere Console: http://localhost:30880
echo   用户名: admin
echo   密码: P@88w0rd
echo ========================================
echo.
echo 后续步骤:
echo   1. 登录 KubeSphere 控制台
echo   2. 进入 扩展市场 安装 DevOps 组件
echo   3. 进入 扩展市场 安装 GitLab (应用商店)
echo   4. 创建 DevOps 项目，导入流水线
echo.
pause
