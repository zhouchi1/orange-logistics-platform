package com.orange.logistics.order.feign;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class WaybillFeignFallback implements WaybillFeignClient {

    @Override
    public Map<String, Object> generateWaybill(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("waybillNo", "");
        result.put("fallback", true);
        return result;
    }
}
