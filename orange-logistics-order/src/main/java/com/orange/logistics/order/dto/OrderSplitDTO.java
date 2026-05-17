package com.orange.logistics.order.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderSplitDTO {
    private Long orderId;
    private List<SplitItem> splitItems;

    @Data
    public static class SplitItem {
        private String itemDescription;
        private Integer itemCount;
        private java.math.BigDecimal weight;
    }
}
