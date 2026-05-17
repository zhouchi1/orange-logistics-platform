package com.orange.logistics.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.entity.Bill;
import com.orange.logistics.billing.repository.BillMapper;
import com.orange.logistics.billing.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final BillMapper billMapper;

    @Override
    @Transactional
    public void settleBills(List<Long> billIds) {
        List<Bill> bills = billMapper.selectBatchIds(billIds);
        for (Bill bill : bills) {
            if (bill.getPaymentStatus() != 2) {
                log.warn("账单未支付，跳过结算: {}", bill.getBillNo());
                continue;
            }
            if (bill.getSettlementStatus() == 2) {
                log.warn("账单已结算，跳过: {}", bill.getBillNo());
                continue;
            }
            bill.setSettlementStatus(2);
            bill.setSettlementTime(LocalDateTime.now());
            bill.setUpdateTime(LocalDateTime.now());
            billMapper.updateById(bill);
        }
        log.info("批量结算完成: {}笔", billIds.size());
    }

    @Override
    @Transactional
    public void autoSettle(LocalDate date) {
        // 自动结算指定日期之前已支付但未结算的账单
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getPaymentStatus, 2);
        wrapper.eq(Bill::getSettlementStatus, 1);
        wrapper.lt(Bill::getPayTime, date.atTime(LocalTime.MAX));
        List<Bill> bills = billMapper.selectList(wrapper);

        for (Bill bill : bills) {
            bill.setSettlementStatus(2);
            bill.setSettlementTime(LocalDateTime.now());
            bill.setUpdateTime(LocalDateTime.now());
            billMapper.updateById(bill);
        }
        log.info("自动结算完成: 日期={} 笔数={}", date, bills.size());
    }

    @Override
    public Page<Bill> pageSettledBills(LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getSettlementStatus, 2);
        if (startDate != null) {
            wrapper.ge(Bill::getSettlementTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(Bill::getSettlementTime, endDate.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(Bill::getSettlementTime);
        return billMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
