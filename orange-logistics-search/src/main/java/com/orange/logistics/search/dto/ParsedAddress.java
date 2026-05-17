package com.orange.logistics.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地址解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedAddress {

    /**
     * 原始地址
     */
    private String rawAddress;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     *      */
    private String district;

    /**
     * 街道
     */
    private String street;

    /**
     * 门牌     */
    private String houseNumber;

    /**
     * 详细地址（街门牌号之后的部分析     */
    private String detail;

    /**
     * 解析置信(0-1)
     */
    private Double confidence;
}
