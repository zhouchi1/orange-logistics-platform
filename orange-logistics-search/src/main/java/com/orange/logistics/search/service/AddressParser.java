package com.orange.logistics.search.service;

import com.orange.logistics.search.dto.ParsedAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 地址解析服务
 * 正则拆分省/市/区/街道/门牌号
 */
@Slf4j
@Service
public class AddressParser {

    /**
     * 地址解析正则表达式
     * 匹配：省/自治区/直辖市 + 市 + 区/县 + 街道/路 + 门牌号 + 详细地址
     */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?<province>[^省]+省|[^自治区]+自治区|北京|天津|上海|重庆|香港|澳门)?" +
            "(?<city>[^市]+市|[^州]+州|[^盟]+盟)?" +
            "(?<district>[^区]+区|[^县]+县|[^旗]+旗|[^市]+市)?" +
            "(?<street>[^街道]+街道|[^路]+路|[^巷]+巷|[^大道]+大道)?" +
            "(?<houseNumber>\\d+号)?" +
            "(?<detail>.*)?"
    );

    /**
     * 解析地址
     */
    public Mono<ParsedAddress> parse(String rawAddress) {
        return Mono.fromCallable(() -> {
            if (rawAddress == null || rawAddress.trim().isEmpty()) {
                return ParsedAddress.builder()
                        .rawAddress(rawAddress)
                        .confidence(0.0)
                        .build();
            }

            String address = rawAddress.trim();
            Matcher matcher = ADDRESS_PATTERN.matcher(address);

            if (matcher.matches()) {
                String province = matcher.group("province");
                String city = matcher.group("city");
                String district = matcher.group("district");
                String street = matcher.group("street");
                String houseNumber = matcher.group("houseNumber");
                String detail = matcher.group("detail");

                // 计算置信度：匹配到的字段越多，置信度越高
                int matchedFields = 0;
                if (province != null && !province.isEmpty()) matchedFields++;
                if (city != null && !city.isEmpty()) matchedFields++;
                if (district != null && !district.isEmpty()) matchedFields++;
                if (street != null && !street.isEmpty()) matchedFields++;
                if (houseNumber != null && !houseNumber.isEmpty()) matchedFields++;

                double confidence = matchedFields / 5.0;

                return ParsedAddress.builder()
                        .rawAddress(rawAddress)
                        .province(trimSuffix(province, "省", "自治区"))
                        .city(trimSuffix(city, "市", "州", "盟"))
                        .district(trimSuffix(district, "区", "县", "旗"))
                        .street(street)
                        .houseNumber(houseNumber)
                        .detail(detail != null ? detail.trim() : null)
                        .confidence(confidence)
                        .build();
            }

            // 无法解析，返回原始地址
            return ParsedAddress.builder()
                    .rawAddress(rawAddress)
                    .detail(rawAddress)
                    .confidence(0.1)
                    .build();
        });
    }

    /**
     * 去除行政区划后缀
     */
    private String trimSuffix(String value, String... suffixes) {
        if (value == null) return null;
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() > suffix.length()) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }
}
