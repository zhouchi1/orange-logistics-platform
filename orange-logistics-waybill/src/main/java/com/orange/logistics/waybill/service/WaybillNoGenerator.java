package com.orange.logistics.waybill.service;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 运单号生成器
 * 规则：OG + 日期(6位) + 区域(2位) + 序列(6位) + 校验(1位)
 * 示例：OG260516BJ000001X
 *
 * 使用 Redis 自增序列保证唯一性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaybillNoGenerator {

    private final StringRedisTemplate redisTemplate;

    private static final String SEQ_KEY_PREFIX = "waybill:seq:";
    private static final int MAX_SEQ = 999999;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * 区域码映射（省份简称 → 2位区域码）
     */
    private static final java.util.Map<String, String> REGION_CODES = java.util.Map.ofEntries(
            java.util.Map.entry("北京", "BJ"), java.util.Map.entry("上海", "SH"),
            java.util.Map.entry("广东", "GD"), java.util.Map.entry("深圳", "SZ"),
            java.util.Map.entry("浙江", "ZJ"), java.util.Map.entry("江苏", "JS"),
            java.util.Map.entry("四川", "SC"), java.util.Map.entry("湖北", "HB"),
            java.util.Map.entry("湖南", "HN"), java.util.Map.entry("河南", "HA"),
            java.util.Map.entry("河北", "HE"), java.util.Map.entry("山东", "SD"),
            java.util.Map.entry("福建", "FJ"), java.util.Map.entry("安徽", "AH"),
            java.util.Map.entry("江西", "JX"), java.util.Map.entry("山西", "SX"),
            java.util.Map.entry("陕西", "SN"), java.util.Map.entry("辽宁", "LN"),
            java.util.Map.entry("吉林", "JL"), java.util.Map.entry("黑龙江", "HL"),
            java.util.Map.entry("天津", "TJ"), java.util.Map.entry("重庆", "CQ"),
            java.util.Map.entry("广西", "GX"), java.util.Map.entry("云南", "YN"),
            java.util.Map.entry("贵州", "GZ"), java.util.Map.entry("海南", "HI"),
            java.util.Map.entry("甘肃", "GS"), java.util.Map.entry("青海", "QH"),
            java.util.Map.entry("内蒙古", "NM"), java.util.Map.entry("宁夏", "NX"),
            java.util.Map.entry("新疆", "XJ"), java.util.Map.entry("西藏", "XZ"),
            java.util.Map.entry("台湾", "TW"), java.util.Map.entry("香港", "HK"),
            java.util.Map.entry("澳门", "MO")
    );

    /**
     * 生成运单号
     *
     * @param region 区域（省份名称或城市名称）
     * @return 运单号
     */
    @Operation(summary = "生成运单号")
    public String generate(String region) {
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        String regionCode = resolveRegionCode(region);
        long seq = getNextSequence(dateStr, regionCode);

        if (seq > MAX_SEQ) {
            log.error("运单号序列溢出: date={}, region={}, seq={}", dateStr, regionCode, seq);
            throw new RuntimeException("当日运单号已用尽，请联系管理员");
        }

        // 组装运单号（不含校验位）
        String baseNo = String.format("OG%s%s%06d", dateStr, regionCode, seq);

        // 计算校验位
        char checkDigit = calculateCheckDigit(baseNo);

        String waybillNo = baseNo + checkDigit;
        log.debug("生成运单号: {}, region={}", waybillNo, region);
        return waybillNo;
    }

    /**
     * 批量生成运单号
     *
     * @param region 区域
     * @param count  数量
     * @return 运单号列表
     */
    @Operation(summary = "批量生成运单号")
    public java.util.List<String> batchGenerate(String region, int count) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("批量生成数量必须在1-1000之间");
        }

        java.util.List<String> waybillNos = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            waybillNos.add(generate(region));
        }
        return waybillNos;
    }

    /**
     * 验证运单号格式和校验位
     *
     * @param waybillNo 运单号
     * @return 是否有效
     */
    @Operation(summary = "验证运单号")
    public boolean validate(String waybillNo) {
        if (waybillNo == null || waybillNo.length() != 17) {
            return false;
        }
        if (!waybillNo.startsWith("OG")) {
            return false;
        }

        // 提取校验位
        String baseNo = waybillNo.substring(0, 16);
        char expectedCheck = calculateCheckDigit(baseNo);
        return waybillNo.charAt(16) == expectedCheck;
    }

    /**
     * 从Redis获取下一个序列号
     */
    private long getNextSequence(String date, String regionCode) {
        String key = SEQ_KEY_PREFIX + date + ":" + regionCode;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 首次创建，设置过期时间为2天（防止跨天问题）
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        return seq != null ? seq : 1L;
    }

    /**
     * 解析区域码
     * 支持省份全称、简称、城市名
     */
    private String resolveRegionCode(String region) {
        if (region == null || region.isBlank()) {
            return "QT"; // 其他
        }

        // 直接匹配省份
        for (java.util.Map.Entry<String, String> entry : REGION_CODES.entrySet()) {
            if (region.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 城市名匹配（常见城市）
        if (region.contains("广州") || region.contains("东莞") || region.contains("佛山")) return "GD";
        if (region.contains("杭州") || region.contains("宁波") || region.contains("温州")) return "ZJ";
        if (region.contains("南京") || region.contains("苏州") || region.contains("无锡")) return "JS";
        if (region.contains("成都")) return "SC";
        if (region.contains("武汉")) return "HB";
        if (region.contains("长沙")) return "HN";
        if (region.contains("郑州")) return "HA";
        if (region.contains("济南") || region.contains("青岛")) return "SD";

        return "QT"; // 未匹配到，使用"其他"
    }

    /**
     * 计算校验位（Luhn算法变体）
     * 对运单号中每个字符的ASCII值加权求和，取模36后映射为0-9A-Z
     */
    private char calculateCheckDigit(String baseNo) {
        int sum = 0;
        for (int i = 0; i < baseNo.length(); i++) {
            int value = baseNo.charAt(i);
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += value * weight;
        }
        int remainder = sum % 36;
        if (remainder < 10) {
            return (char) ('0' + remainder);
        } else {
            return (char) ('A' + remainder - 10);
        }
    }
}
