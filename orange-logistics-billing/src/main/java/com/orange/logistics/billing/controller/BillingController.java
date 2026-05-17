package com.orange.logistics.billing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.dto.FreightCalcDTO;
import com.orange.logistics.billing.dto.FreightCalcResult;
import com.orange.logistics.billing.entity.Bill;
import com.orange.logistics.billing.service.BillingService;
import com.orange.logistics.billing.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "计费管理", description = "运费计算、账单管理、结算")
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final SettlementService settlementService;

    @Operation(summary = "运费计算")
    @PostMapping("/calculate")
    public ResponseEntity<FreightCalcResult> calculateFreight(@Valid @RequestBody FreightCalcDTO dto) {
        return ResponseEntity.ok(billingService.calculateFreight(dto));
    }

    @Operation(summary = "运费计算(Map参数，供Feign调用)")
    @PostMapping("/calculate/map")
    public ResponseEntity<Map<String, Object>> calculateFreightMap(@RequestBody Map<String, Object> params) {
        return ResponseEntity.ok(billingService.calculateFreightMap(params));
    }

    @Operation(summary = "创建账单")
    @PostMapping("/bill")
    public ResponseEntity<Bill> createBill(
            @RequestParam String orderNo,
            @RequestParam(required = false) String waybillNo,
            @RequestParam Long customerId,
            @Valid @RequestBody FreightCalcDTO calcDto) {
        return ResponseEntity.ok(billingService.createBill(orderNo, waybillNo, customerId, calcDto));
    }

    @Operation(summary = "查询账单详情")
    @GetMapping("/bill/{id}")
    public ResponseEntity<Bill> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getById(id));
    }

    @Operation(summary = "分页查询账单")
    @GetMapping("/bill/page")
    public ResponseEntity<Page<Bill>> pageBills(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer paymentStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(billingService.pageBills(customerId, paymentStatus, page, size));
    }

    @Operation(summary = "支付账单")
    @PostMapping("/bill/{id}/pay")
    public ResponseEntity<String> payBill(@PathVariable Long id) {
        billingService.payBill(id);
        return ResponseEntity.ok("支付成功");
    }

    @Operation(summary = "退款")
    @PostMapping("/bill/{id}/refund")
    public ResponseEntity<String> refundBill(@PathVariable Long id) {
        billingService.refundBill(id);
        return ResponseEntity.ok("退款成功");
    }

    @Operation(summary = "批量结算")
    @PostMapping("/settlement/batch")
    public ResponseEntity<String> settleBills(@RequestBody List<Long> billIds) {
        settlementService.settleBills(billIds);
        return ResponseEntity.ok("结算完成");
    }

    @Operation(summary = "自动结算")
    @PostMapping("/settlement/auto")
    public ResponseEntity<String> autoSettle(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        settlementService.autoSettle(date);
        return ResponseEntity.ok("自动结算完成");
    }

    @Operation(summary = "查询已结算账单")
    @GetMapping("/settlement/page")
    public ResponseEntity<Page<Bill>> pageSettledBills(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(settlementService.pageSettledBills(startDate, endDate, page, size));
    }
}
