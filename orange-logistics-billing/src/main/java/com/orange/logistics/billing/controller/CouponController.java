package com.orange.logistics.billing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.entity.Coupon;
import com.orange.logistics.billing.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "优惠券管理", description = "优惠券创建、查询、使用")
@RestController
@RequestMapping("/api/billing/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "创建优惠券")
    @PostMapping
    public ResponseEntity<Coupon> createCoupon(
            @RequestParam String name,
            @RequestParam Integer type,
            @RequestParam BigDecimal threshold,
            @RequestParam BigDecimal discount,
            @RequestParam(required = false) BigDecimal maxDiscount,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ResponseEntity.ok(couponService.createCoupon(name, type, threshold, discount,
                maxDiscount, customerId, startTime, endTime));
    }

    @Operation(summary = "查询可用优惠券")
    @GetMapping("/available")
    public ResponseEntity<List<Coupon>> getAvailableCoupons(
            @RequestParam Long customerId,
            @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(couponService.getAvailableCoupons(customerId, amount));
    }

    @Operation(summary = "查询优惠券详情")
    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getById(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.getById(id));
    }

    @Operation(summary = "分页查询优惠券")
    @GetMapping("/page")
    public ResponseEntity<Page<Coupon>> pageCoupons(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(couponService.pageCoupons(customerId, status, page, size));
    }
}
