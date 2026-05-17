package com.orange.logistics.order.feign;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class BillingFeignFallback implements BillingFeignClient {

    @Override
    public Map<String, Object> calculateFreight(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("freight", BigDecimal.ZERO);
        result.put("fallback", true);
        return result;
    }
}
