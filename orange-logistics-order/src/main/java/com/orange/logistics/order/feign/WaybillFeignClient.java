package com.orange.logistics.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "orange-logistics-waybill", fallback = WaybillFeignFallback.class)
public interface WaybillFeignClient {

    @PostMapping("/api/waybill/generate")
    Map<String, Object> generateWaybill(@RequestBody Map<String, Object> params);
}
