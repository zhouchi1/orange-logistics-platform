package com.orange.logistics.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "orange-logistics-billing", fallback = BillingFeignFallback.class)
public interface BillingFeignClient {

    @PostMapping("/api/billing/calculate")
    Map<String, Object> calculateFreight(@RequestBody Map<String, Object> params);
}
