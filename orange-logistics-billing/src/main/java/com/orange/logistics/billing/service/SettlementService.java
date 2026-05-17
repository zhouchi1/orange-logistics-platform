package com.orange.logistics.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.entity.Bill;

import java.time.LocalDate;
import java.util.List;

public interface SettlementService {
    void settleBills(List<Long> billIds);
    void autoSettle(LocalDate date);
    Page<Bill> pageSettledBills(LocalDate startDate, LocalDate endDate, int page, int size);
}
