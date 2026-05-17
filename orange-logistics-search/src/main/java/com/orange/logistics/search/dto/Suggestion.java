package com.orange.logistics.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Suggestion {

    /**
     * 建议文本
     */
    private String text;

    /**
     * 建议类型：HISTORY / HOT / PREFIX
     */
    private String type;

    /**
     * 热度分数
     */
    private Long score;
}
