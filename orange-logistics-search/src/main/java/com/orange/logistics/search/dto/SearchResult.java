package com.orange.logistics.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /**
     * 总命中数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 搜索耗时（毫秒）
     */
    private Long took;

    /**
     * 命中文档列表
     */
    private List<SearchHit> hits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchHit {
        /**
         * 文档ID
         */
        private String id;

        /**
         * 运单         */
        private String waybillNo;

        /**
         * 收件         */
        private String receiverName;

        /**
         * 收件人手         */
        private String receiverPhone;

        /**
         * 收件地址
         */
        private String receiverAddress;

        /**
         * 订单状         */
        private String status;

        /**
         * 相关度分析         */
        private Float score;

        /**
         * 高亮片段
         */
        private String highlight;
    }
}
