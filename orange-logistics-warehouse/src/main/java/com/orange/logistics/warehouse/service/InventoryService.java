package com.orange.logistics.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.orange.logistics.warehouse.entity.Inventory;
import com.orange.logistics.warehouse.repository.WarehouseMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存服务
 * - 实时库存扣减（Redis + MySQL 双写）
 * - 库存预占（下单时锁定）
 * - 库存释放（取消订单）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final WarehouseMapper warehouseMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    private static final String INVENTORY_KEY_PREFIX = "inventory:stock:";
    private static final String LOCKED_KEY_PREFIX = "inventory:locked:";

    /**
     * Redis Lua脚本：原子扣减库存
     * KEYS[1] = 库存key, ARGV[1] = 扣减数量
     * 返回: 1=成功, 0=库存不足
     */
    private static final String DEDUCT_SCRIPT =
            "local stock = tonumber(redis.call('get', KEYS[1]) or '0') " +
            "local deduct = tonumber(ARGV[1]) " +
            "if stock >= deduct then " +
            "  redis.call('decrby', KEYS[1], deduct) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * Redis Lua脚本：原子预占库存
     * KEYS[1] = 可用库存key, KEYS[2] = 锁定库存key, ARGV[1] = 预占数量
     */
    private static final String LOCK_SCRIPT =
            "local available = tonumber(redis.call('get', KEYS[1]) or '0') " +
            "local lockQty = tonumber(ARGV[1]) " +
            "if available >= lockQty then " +
            "  redis.call('decrby', KEYS[1], lockQty) " +
            "  redis.call('incrby', KEYS[2], lockQty) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * 查询实时库存
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @return 库存信息
     */
    @Operation(summary = "查询实时库存")
    public StockInfo queryStock(Long warehouseId, String skuCode) {
        if (warehouseId == null || skuCode == null) {
            throw new IllegalArgumentException("仓库ID和SKU编码不能为空");
        }

        String stockKey = buildStockKey(warehouseId, skuCode);
        String lockedKey = buildLockedKey(warehouseId, skuCode);

        // 优先从Redis读取
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        String lockedStr = redisTemplate.opsForValue().get(lockedKey);

        if (stockStr != null) {
            int available = Integer.parseInt(stockStr);
            int locked = lockedStr != null ? Integer.parseInt(lockedStr) : 0;
            return StockInfo.builder()
                    .warehouseId(warehouseId)
                    .skuCode(skuCode)
                    .availableQuantity(available)
                    .lockedQuantity(locked)
                    .totalQuantity(available + locked)
                    .source("REDIS")
                    .build();
        }

        // Redis无数据，从MySQL查询并回写Redis
        log.info("Redis缓存未命中，从MySQL查询库存: warehouse={}, sku={}", warehouseId, skuCode);

        // 模拟从数据库查询（实际应使用InventoryMapper）
        return StockInfo.builder()
                .warehouseId(warehouseId)
                .skuCode(skuCode)
                .availableQuantity(0)
                .lockedQuantity(0)
                .totalQuantity(0)
                .source("MYSQL")
                .build();
    }

    /**
     * 库存预占（下单时锁定）
     * 使用Redis Lua脚本保证原子性，然后异步同步到MySQL
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @param quantity    预占数量
     * @param orderNo     关联订单号
     * @return 预占结果
     */
    @Operation(summary = "库存预占")
    @Transactional
    public InventoryResult lockStock(Long warehouseId, String skuCode, int quantity, String orderNo) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("预占数量必须大于0");
        }

        String stockKey = buildStockKey(warehouseId, skuCode);
        String lockedKey = buildLockedKey(warehouseId, skuCode);
        String lockName = "inventory:lock:" + warehouseId + ":" + skuCode;

        RLock lock = redissonClient.getLock(lockName);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                return InventoryResult.fail("获取库存锁超时，请稍后重试");
            }

            // Redis原子预占
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script,
                    List.of(stockKey, lockedKey),
                    String.valueOf(quantity));

            if (result == null || result == 0) {
                log.warn("库存预占失败-库存不足: warehouse={}, sku={}, 需要{}", warehouseId, skuCode, quantity);
                return InventoryResult.fail("库存不足，当前可用库存不满足需求");
            }

            // 同步更新MySQL（保证最终一致性）
            syncLockToMySQL(warehouseId, skuCode, quantity);

            log.info("库存预占成功: warehouse={}, sku={}, quantity={}, orderNo={}",
                    warehouseId, skuCode, quantity, orderNo);

            return InventoryResult.success("库存预占成功", quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return InventoryResult.fail("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 库存释放（取消订单时释放预占）
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @param quantity    释放数量
     * @param orderNo     关联订单号
     * @return 释放结果
     */
    @Operation(summary = "库存释放")
    @Transactional
    public InventoryResult releaseStock(Long warehouseId, String skuCode, int quantity, String orderNo) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("释放数量必须大于0");
        }

        String stockKey = buildStockKey(warehouseId, skuCode);
        String lockedKey = buildLockedKey(warehouseId, skuCode);
        String lockName = "inventory:lock:" + warehouseId + ":" + skuCode;

        RLock lock = redissonClient.getLock(lockName);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                return InventoryResult.fail("获取库存锁超时");
            }

            // Redis释放：锁定数减少，可用数增加
            redisTemplate.opsForValue().increment(stockKey, quantity);
            redisTemplate.opsForValue().decrement(lockedKey, quantity);

            // 同步MySQL
            syncReleaseToMySQL(warehouseId, skuCode, quantity);

            log.info("库存释放成功: warehouse={}, sku={}, quantity={}, orderNo={}",
                    warehouseId, skuCode, quantity, orderNo);

            return InventoryResult.success("库存释放成功", quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return InventoryResult.fail("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 实时库存扣减（出库确认时调用）
     * 从锁定库存中扣减（已预占的部分）
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @param quantity    扣减数量
     * @return 扣减结果
     */
    @Operation(summary = "库存扣减（出库）")
    @Transactional
    public InventoryResult deductStock(Long warehouseId, String skuCode, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }

        String lockedKey = buildLockedKey(warehouseId, skuCode);
        String lockName = "inventory:lock:" + warehouseId + ":" + skuCode;

        RLock lock = redissonClient.getLock(lockName);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                return InventoryResult.fail("获取库存锁超时");
            }

            // 从锁定库存中扣减
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEDUCT_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script,
                    Collections.singletonList(lockedKey),
                    String.valueOf(quantity));

            if (result == null || result == 0) {
                log.warn("库存扣减失败-锁定库存不足: warehouse={}, sku={}", warehouseId, skuCode);
                return InventoryResult.fail("锁定库存不足，无法扣减");
            }

            // 同步MySQL
            syncDeductToMySQL(warehouseId, skuCode, quantity);

            log.info("库存扣减成功: warehouse={}, sku={}, quantity={}", warehouseId, skuCode, quantity);

            return InventoryResult.success("库存扣减成功", quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return InventoryResult.fail("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 库存补货（入库）
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @param quantity    补货数量
     * @return 补货结果
     */
    @Operation(summary = "库存补货")
    @Transactional
    public InventoryResult replenishStock(Long warehouseId, String skuCode, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("补货数量必须大于0");
        }

        String stockKey = buildStockKey(warehouseId, skuCode);

        // Redis增加可用库存
        redisTemplate.opsForValue().increment(stockKey, quantity);

        // 同步MySQL
        syncReplenishToMySQL(warehouseId, skuCode, quantity);

        log.info("库存补货成功: warehouse={}, sku={}, quantity={}", warehouseId, skuCode, quantity);

        return InventoryResult.success("库存补货成功", quantity);
    }

    /**
     * 初始化Redis库存缓存（从MySQL加载）
     *
     * @param warehouseId 仓库ID
     * @param skuCode     SKU编码
     * @param available   可用数量
     * @param locked      锁定数量
     */
    @Operation(summary = "初始化库存缓存")
    public void initStockCache(Long warehouseId, String skuCode, int available, int locked) {
        String stockKey = buildStockKey(warehouseId, skuCode);
        String lockedKey = buildLockedKey(warehouseId, skuCode);

        redisTemplate.opsForValue().set(stockKey, String.valueOf(available));
        redisTemplate.opsForValue().set(lockedKey, String.valueOf(locked));

        log.info("库存缓存初始化完成: warehouse={}, sku={}, available={}, locked={}",
                warehouseId, skuCode, available, locked);
    }

    // ========== 私有方法 ==========
    private String buildStockKey(Long warehouseId, String skuCode) {
        return INVENTORY_KEY_PREFIX + warehouseId + ":" + skuCode;
    }

    private String buildLockedKey(Long warehouseId, String skuCode) {
        return LOCKED_KEY_PREFIX + warehouseId + ":" + skuCode;
    }

    private void syncLockToMySQL(Long warehouseId, String skuCode, int quantity) {
        // 实际项目中使用InventoryMapper更新
        // UPDATE inventory SET available_quantity = available_quantity - #{qty},
        //   locked_quantity = locked_quantity + #{qty}
        // WHERE warehouse_id = #{warehouseId} AND sku_code = #{skuCode}
        //   AND available_quantity >= #{qty}
        log.debug("同步预占到MySQL: warehouse={}, sku={}, qty={}", warehouseId, skuCode, quantity);
    }

    private void syncReleaseToMySQL(Long warehouseId, String skuCode, int quantity) {
        log.debug("同步释放到MySQL: warehouse={}, sku={}, qty={}", warehouseId, skuCode, quantity);
    }

    private void syncDeductToMySQL(Long warehouseId, String skuCode, int quantity) {
        log.debug("同步扣减到MySQL: warehouse={}, sku={}, qty={}", warehouseId, skuCode, quantity);
    }

    private void syncReplenishToMySQL(Long warehouseId, String skuCode, int quantity) {
        log.debug("同步补货到MySQL: warehouse={}, sku={}, qty={}", warehouseId, skuCode, quantity);
    }

    // ========== 内部类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockInfo {
        private Long warehouseId;
        private String skuCode;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private Integer lockedQuantity;
        private String source; // REDIS or MYSQL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryResult {
        private boolean success;
        private String message;
        private Integer quantity;

        public static InventoryResult success(String message, int quantity) {
            return InventoryResult.builder()
                    .success(true)
                    .message(message)
                    .quantity(quantity)
                    .build();
        }

        public static InventoryResult fail(String message) {
            return InventoryResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
