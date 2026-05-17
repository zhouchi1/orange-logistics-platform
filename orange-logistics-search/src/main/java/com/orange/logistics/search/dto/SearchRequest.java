package com.orange.logistics.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    /**
     * 搜索关键     */
    private String keyword;

    /**
     * 搜索类型：ALL / WAYBILL / ADDRESS / RECEIVER
     */
    private String type;

    /**
     * 页码（从1开始）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 排序字段
     */
    private String sortBy;

    /**
     * 排序方向：ASC / DESC
     */
    private String sortOrder;

    public Integer getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public Integer getSize() {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }
}
