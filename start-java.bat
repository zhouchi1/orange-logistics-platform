@echo off
set PROJECT_DIR=C:\Users\21779\Desktop\orange-logistics-platform
set JAVA_OPTS=-Xms128m -Xmx256m -Dfile.encoding=UTF-8
set NACOS_OPTS=-Dspring.cloud.nacos.discovery.enabled=false -Dspring.cloud.nacos.config.enabled=false
set DB_OPTS=-DMYSQL_HOST=localhost -DREDIS_HOST=localhost

echo [%time%] Starting Gateway (8888)...
start "gateway-8888" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-gateway\target\orange-logistics-gateway-1.0.0-SNAPSHOT.jar"

echo [%time%] Starting Auth (8100)...
start "auth-8100" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-auth\target\orange-logistics-auth-1.0.0-SNAPSHOT.jar"

echo [%time%] Starting Order (8101)...
start "order-8101" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -Dseata.enabled=false -jar "%PROJECT_DIR%\orange-logistics-order\target\orange-logistics-order-1.0.0-SNAPSHOT.jar"

echo [%time%] Starting Transport (8083)...
start "transport-8083" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-transport\target\orange-logistics-transport-1.0.0-SNAPSHOT.jar"

echo [%time%] Starting Dispatch (8084)...
start "dispatch-8084" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-dispatch\target\orange-logistics-dispatch-1.0.0-SNAPSHOT.jar"

echo [%time%] Starting Config (8092)...
start "config-8092" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-config\target\orange-logistics-config-1.0.0-SNAPSHOT.jar"

echo.
echo All 6 Java services launched. They need ~90s to fully start.
echo Ports: Gateway=8888, Auth=8100, Order=8101, Transport=8083, Dispatch=8084, Config=8092
