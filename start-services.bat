@echo off
set PROJECT_DIR=C:\Users\21779\Desktop\orange-logistics-platform
set JAVA_OPTS=-Xms128m -Xmx256m -Dfile.encoding=UTF-8
set NACOS_OPTS=-Dspring.cloud.nacos.discovery.enabled=false -Dspring.cloud.nacos.config.enabled=false
set DB_OPTS=-DMYSQL_HOST=localhost -DREDIS_HOST=localhost

echo Starting Order...
start "order-8101" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -Dseata.enabled=false -jar "%PROJECT_DIR%\orange-logistics-order\target\orange-logistics-order-1.0.0-SNAPSHOT.jar"

echo Starting Transport...
start "transport-8083" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-transport\target\orange-logistics-transport-1.0.0-SNAPSHOT.jar"

echo Starting Dispatch...
start "dispatch-8084" /MIN java %JAVA_OPTS% %NACOS_OPTS% %DB_OPTS% -jar "%PROJECT_DIR%\orange-logistics-dispatch\target\orange-logistics-dispatch-1.0.0-SNAPSHOT.jar"

echo All launched.
