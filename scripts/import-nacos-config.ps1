# Nacos 配置导入脚本
$nacosUrl = "http://localhost:8848/nacos/v1/cs/configs"

$configs = @(
    @{
        dataId = "common.yml"
        type = "yaml"
        content = @"
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null
logging:
  level:
    root: INFO
    com.orange.logistics: DEBUG
orange:
  logistics:
    waybill-prefix: OG
    default-timeout-hours: 72
    max-retry-count: 3
"@
    },
    @{
        dataId = "datasource.yml"
        type = "yaml"
        content = @"
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:13306/orange_logistics
    username: root
    password: orange_logistics_2024
    pool:
      initial-size: 5
      max-size: 20
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: orange-logistics-group
      auto-offset-reset: earliest
    producer:
      acks: all
      retries: 3
  elasticsearch:
    uris: http://localhost:9200
"@
    },
    @{
        dataId = "redis.yml"
        type = "yaml"
        content = @"
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
"@
    }
)

foreach ($cfg in $configs) {
    $body = @{
        dataId = $cfg.dataId
        group = "DEFAULT_GROUP"
        content = $cfg.content
        type = $cfg.type
    }
    try {
        $r = Invoke-WebRequest -Uri $nacosUrl -Method POST -Body $body -UseBasicParsing -TimeoutSec 5
        Write-Host "[OK] $($cfg.dataId): $($r.Content)" -ForegroundColor Green
    } catch {
        Write-Host "[FAIL] $($cfg.dataId): $($_.Exception.Message)" -ForegroundColor Red
    }
}
