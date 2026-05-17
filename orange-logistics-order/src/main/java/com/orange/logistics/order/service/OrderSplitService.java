package com.orange.logistics.order.service;

import com.orange.logistics.order.dto.OrderSplitDTO;
import com.orange.logistics.order.entity.LogisticsOrder;
import com.orange.logistics.order.enums.OrderStatus;
import com.orange.logistics.order.repository.OrderMapper;
import com.orange.logistics.order.vo.OrderVO;
import cn.hutool.core.util.IdUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单拆分服务
 * 支持按仓库拆分（不同商品在不同仓）和按重量拆分（超重拆包）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSplitService {

    private final OrderMapper orderMapper;

    /**
     * 单包最大重量(kg)
     */
    private static final BigDecimal MAX_PACKAGE_WEIGHT = new BigDecimal("30");

    /**
     * 按仓库拆分订单
     * 当订单中的商品分布在不同仓库时，需要拆分为多个子订单
     *
     * @param orderId        原订单ID
     * @param warehouseItems 仓库-商品映射 (warehouseId -> items)
     * @return 拆分后的子订单列表
     */
    @Operation(summary = "按仓库拆分订单")
    @Transactional
    public List<LogisticsOrder> splitByWarehouse(Long orderId, Map<Long, List<WarehouseItem>> warehouseItems) {
        LogisticsOrder parentOrder = orderMapper.selectById(orderId);
        if (parentOrder == null) {
            throw new IllegalArgumentException("原订单不存在: " + orderId);
        }
        if (parentOrder.getStatus() != OrderStatus.CREATED.getCode()) {
            throw new IllegalStateException("只有已创建状态的订单才能拆分，当前状态: "
                    + OrderStatus.fromCode(parentOrder.getStatus()).getDesc());
        }
        if (warehouseItems == null || warehouseItems.size() <= 1) {
            throw new IllegalArgumentException("仓库拆分至少需要2个不同仓库的商品");
        }

        List<LogisticsOrder> subOrders = new ArrayList<>();

        for (Map.Entry<Long, List<WarehouseItem>> entry : warehouseItems.entrySet()) {
            Long warehouseId = entry.getKey();
            List<WarehouseItem> items = entry.getValue();

            // 计算子订单的总重量和件数
            BigDecimal totalWeight = items.stream()
                    .map(WarehouseItem::getWeight)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int totalCount = items.stream()
                    .mapToInt(WarehouseItem::getQuantity)
                    .sum();
            String itemDesc = items.stream()
                    .map(WarehouseItem::getItemName)
                    .collect(Collectors.joining(", "));

            LogisticsOrder subOrder = buildSubOrder(parentOrder);
            subOrder.setWeight(totalWeight);
            subOrder.setItemCount(totalCount);
            subOrder.setItemDescription(itemDesc);
            subOrder.setRemark("仓库拆分-仓库ID:" + warehouseId);
            orderMapper.insert(subOrder);
            subOrders.add(subOrder);

            log.info("仓库拆分子订单: {} -> 仓库={}, 件数={}, 重量={}kg",
                    subOrder.getOrderNo(), warehouseId, totalCount, totalWeight);
        }

        // 标记原订单为已取消（已拆分）
        markParentAsSplit(parentOrder, subOrders.size());

        log.info("订单按仓库拆分完成: {} -> {} 个子订单", parentOrder.getOrderNo(), subOrders.size());
        return subOrders;
    }

    /**
     * 按重量拆分订单
     * 当订单总重量超过单包最大重量时，自动拆分为多个包裹
     *
     * @param orderId 原订单ID
     * @return 拆分后的子订单列表（如果不需要拆分则返回原订单）
     */
    @Operation(summary = "按重量拆分订单")
    @Transactional
    public List<LogisticsOrder> splitByWeight(Long orderId) {
        return splitByWeight(orderId, MAX_PACKAGE_WEIGHT);
    }

    /**
     * 按重量拆分订单（自定义最大重量）
     *
     * @param orderId          原订单ID
     * @param maxPackageWeight 单包最大重量
     * @return 拆分后的子订单列表
     */
    @Operation(summary = "按重量拆分订单（自定义阈值）")
    @Transactional
    public List<LogisticsOrder> splitByWeight(Long orderId, BigDecimal maxPackageWeight) {
        LogisticsOrder parentOrder = orderMapper.selectById(orderId);
        if (parentOrder == null) {
            throw new IllegalArgumentException("原订单不存在: " + orderId);
        }
        if (parentOrder.getStatus() != OrderStatus.CREATED.getCode()) {
            throw new IllegalStateException("只有已创建状态的订单才能拆分");
        }

        BigDecimal totalWeight = parentOrder.getWeight();
        if (totalWeight == null || totalWeight.compareTo(maxPackageWeight) <= 0) {
            log.info("订单 {} 重量 {}kg 未超限，无需拆分", parentOrder.getOrderNo(), totalWeight);
            return Collections.singletonList(parentOrder);
        }

        // 计算需要拆分的包裹数
        int packageCount = totalWeight.divide(maxPackageWeight, 0, RoundingMode.CEILING).intValue();
        BigDecimal avgWeight = totalWeight.divide(BigDecimal.valueOf(packageCount), 2, RoundingMode.HALF_UP);
        int avgItemCount = Math.max(1, (parentOrder.getItemCount() != null ? parentOrder.getItemCount() : 1) / packageCount);

        List<LogisticsOrder> subOrders = new ArrayList<>();
        BigDecimal remainingWeight = totalWeight;
        int remainingItems = parentOrder.getItemCount() != null ? parentOrder.getItemCount() : packageCount;

        for (int i = 0; i < packageCount; i++) {
            boolean isLast = (i == packageCount - 1);
            BigDecimal packageWeight = isLast ? remainingWeight : avgWeight;
            int packageItems = isLast ? remainingItems : avgItemCount;

            LogisticsOrder subOrder = buildSubOrder(parentOrder);
            subOrder.setWeight(packageWeight);
            subOrder.setItemCount(packageItems);
            subOrder.setRemark(String.format("重量拆分 %d/%d", i + 1, packageCount));
            orderMapper.insert(subOrder);
            subOrders.add(subOrder);

            remainingWeight = remainingWeight.subtract(packageWeight);
            remainingItems -= packageItems;

            log.info("重量拆分子订单: {} -> 包裹{}/{}, 重量={}kg",
                    subOrder.getOrderNo(), i + 1, packageCount, packageWeight);
        }

        // 标记原订单为已取消（已拆分）
        markParentAsSplit(parentOrder, subOrders.size());

        log.info("订单按重量拆分完成: {} ({}kg) -> {} 个子订单",
                parentOrder.getOrderNo(), totalWeight, subOrders.size());
        return subOrders;
    }

    /**
     * 智能拆分：综合判断是否需要拆分以及拆分方式
     *
     * @param orderId        订单ID
     * @param warehouseItems 仓库商品映射（可为null，表示不按仓库拆分）
     * @return 拆分结果
     */
    @Operation(summary = "智能拆分订单")
    @Transactional
    public SplitResult smartSplit(Long orderId, Map<Long, List<WarehouseItem>> warehouseItems) {
        LogisticsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }

        SplitResult result = new SplitResult();
        result.setOriginalOrderNo(order.getOrderNo());

        // 优先按仓库拆分
        if (warehouseItems != null && warehouseItems.size() > 1) {
            List<LogisticsOrder> warehouseSplit = splitByWarehouse(orderId, warehouseItems);
            result.setSplitType("WAREHOUSE");
            result.setSubOrders(warehouseSplit);
            result.setSplitCount(warehouseSplit.size());

            // 对每个子订单再检查是否需要按重量拆分
            List<LogisticsOrder> finalOrders = new ArrayList<>();
            for (LogisticsOrder subOrder : warehouseSplit) {
                if (subOrder.getWeight() != null && subOrder.getWeight().compareTo(MAX_PACKAGE_WEIGHT) > 0) {
                    List<LogisticsOrder> weightSplit = splitByWeight(subOrder.getId());
                    finalOrders.addAll(weightSplit);
                } else {
                    finalOrders.add(subOrder);
                }
            }
            result.setSubOrders(finalOrders);
            result.setSplitCount(finalOrders.size());
            return result;
        }

        // 仅按重量拆分
        if (order.getWeight() != null && order.getWeight().compareTo(MAX_PACKAGE_WEIGHT) > 0) {
            List<LogisticsOrder> weightSplit = splitByWeight(orderId);
            result.setSplitType("WEIGHT");
            result.setSubOrders(weightSplit);
            result.setSplitCount(weightSplit.size());
            return result;
        }

        // 无需拆分
        result.setSplitType("NONE");
        result.setSubOrders(Collections.singletonList(order));
        result.setSplitCount(1);
        return result;
    }

    // ========== 私有方法 ==========
    private LogisticsOrder buildSubOrder(LogisticsOrder parent) {
        LogisticsOrder subOrder = new LogisticsOrder();
        subOrder.setOrderNo(generateOrderNo());
        subOrder.setCustomerId(parent.getCustomerId());
        subOrder.setSenderName(parent.getSenderName());
        subOrder.setSenderPhone(parent.getSenderPhone());
        subOrder.setSenderAddress(parent.getSenderAddress());
        subOrder.setSenderProvince(parent.getSenderProvince());
        subOrder.setSenderCity(parent.getSenderCity());
        subOrder.setSenderDistrict(parent.getSenderDistrict());
        subOrder.setReceiverName(parent.getReceiverName());
        subOrder.setReceiverPhone(parent.getReceiverPhone());
        subOrder.setReceiverAddress(parent.getReceiverAddress());
        subOrder.setReceiverProvince(parent.getReceiverProvince());
        subOrder.setReceiverCity(parent.getReceiverCity());
        subOrder.setReceiverDistrict(parent.getReceiverDistrict());
        subOrder.setPaymentMethod(parent.getPaymentMethod());
        subOrder.setServiceType(parent.getServiceType());
        subOrder.setExpectedDeliveryTime(parent.getExpectedDeliveryTime());
        subOrder.setParentOrderId(parent.getId());
        subOrder.setStatus(OrderStatus.CREATED.getCode());
        subOrder.setDeleted(0);
        subOrder.setCreateTime(LocalDateTime.now());
        subOrder.setUpdateTime(LocalDateTime.now());
        return subOrder;
    }

    private void markParentAsSplit(LogisticsOrder parentOrder, int splitCount) {
        parentOrder.setStatus(OrderStatus.CANCELLED.getCode());
        parentOrder.setRemark("订单已拆分为" + splitCount + "个子订单");
        parentOrder.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(parentOrder);
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "OG" + date + snowflake.substring(snowflake.length() - 8);
    }

    // ========== 内部类 ==========

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WarehouseItem {
        private String skuCode;
        private String itemName;
        private Integer quantity;
        private BigDecimal weight;
        private Long warehouseId;
    }

    @lombok.Data
    public static class SplitResult {
        private String originalOrderNo;
        private String splitType; // NONE, WAREHOUSE, WEIGHT
    private Integer splitCount;
        private List<LogisticsOrder> subOrders;
    }
}
