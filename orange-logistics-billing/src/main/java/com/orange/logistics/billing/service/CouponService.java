package com.orange.logistics.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {
    Coupon createCoupon(String name, Integer type, BigDecimal threshold, BigDecimal discount,
                        BigDecimal maxDiscount, Long customerId, String startTime, String endTime);
    List<Coupon> getAvailableCoupons(Long customerId, BigDecimal amount);
    Coupon getById(Long id);
    void useCoupon(Long couponId, String orderNo);
    void returnCoupon(Long couponId);
    Page<Coupon> pageCoupons(Long customerId, Integer status, int page, int size);
}
