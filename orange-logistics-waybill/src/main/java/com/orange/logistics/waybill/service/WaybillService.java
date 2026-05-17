package com.orange.logistics.waybill.service;

import com.orange.logistics.waybill.dto.AddTraceDTO;
import com.orange.logistics.waybill.dto.GenerateWaybillDTO;
import com.orange.logistics.waybill.dto.SignConfirmDTO;
import com.orange.logistics.waybill.vo.WaybillVO;

import java.util.List;
import java.util.Map;

public interface WaybillService {
    WaybillVO generateWaybill(GenerateWaybillDTO dto);
    Map<String, Object> generateWaybillFromMap(Map<String, Object> params);
    WaybillVO getByWaybillNo(String waybillNo);
    WaybillVO getById(Long id);
    void addTrace(AddTraceDTO dto);
    void confirmSign(SignConfirmDTO dto);
    List<WaybillVO> getByOrderId(Long orderId);
}
