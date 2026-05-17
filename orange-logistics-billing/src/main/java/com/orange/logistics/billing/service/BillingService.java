package com.orange.logistics.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.dto.FreightCalcDTO;
import com.orange.logistics.billing.dto.FreightCalcResult;
import com.orange.logistics.billing.entity.Bill;

import java.util.Map;

public interface BillingService {
    FreightCalcResult calculateFreight(FreightCalcDTO dto);
    Map<String, Object> calculateFreightMap(Map<String, Object> params);
    Bill createBill(String orderNo, String waybillNo, Long customerId, FreightCalcDTO calcDto);
    Bill getById(Long id);
    Bill getByBillNo(String billNo);
    Page<Bill> pageBills(Long customerId, Integer paymentStatus, int page, int size);
    void payBill(Long billId);
    void refundBill(Long billId);
}
