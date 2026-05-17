package com.orange.logistics.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.entity.Coupon;
import com.orange.logistics.billing.repository.CouponMapper;
import com.orange.logistics.billing.service.CouponService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public Coupon createCoupon(String name, Integer type, BigDecimal threshold, BigDecimal discount,
                               BigDecimal maxDiscount, Long customerId, String startTime, String endTime) {
        Coupon coupon = new Coupon();
        coupon.setCouponCode("CP" + IdUtil.getSnowflakeNextId());
        coupon.setCouponName(name);
        coupon.setCouponType(type);
        coupon.setThreshold(threshold);
        coupon.setDiscount(discount);
        coupon.setMaxDiscount(maxDiscount);
        coupon.setCustomerId(customerId);
        coupon.setStatus(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (startTime != null) coupon.setStartTime(LocalDateTime.parse(startTime, fmt));
        if (endTime != null) coupon.setEndTime(LocalDateTime.parse(endTime, fmt));
        coupon.setDeleted(0);
        coupon.setCreateTime(LocalDateTime.now());
        coupon.setUpdateTime(LocalDateTime.now());
        couponMapper.insert(coupon);
        log.info("创建优惠 {} - {}", coupon.getCouponCode(), name);
        return coupon;
    }

    @Override
    public List<Coupon> getAvailableCoupons(Long customerId, BigDecimal amount) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, 1);
        wrapper.and(w -> w.eq(Coupon::getCustomerId, customerId).or().isNull(Coupon::getCustomerId));
        wrapper.le(Coupon::getStartTime, LocalDateTime.now());
        wrapper.ge(Coupon::getEndTime, LocalDateTime.now());
        if (amount != null) {
            wrapper.le(Coupon::getThreshold, amount);
        }
        return couponMapper.selectList(wrapper);
    }

    @Override
    public Coupon getById(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        return coupon;
    }

    @Override
    @Transactional
    public void useCoupon(Long couponId, String orderNo) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        if (coupon.getStatus() != 1) throw new RuntimeException("优惠券不可用");
        coupon.setStatus(2);
        coupon.setUseTime(LocalDateTime.now());
        coupon.setOrderNo(orderNo);
        coupon.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(coupon);
    }

    @Override
    @Transactional
    public void returnCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        coupon.setStatus(1);
        coupon.setUseTime(null);
        coupon.setOrderNo(null);
        coupon.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(coupon);
    }

    @Override
    public Page<Coupon> pageCoupons(Long customerId, Integer status, int page, int size) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(Coupon::getCustomerId, customerId);
        if (status != null) wrapper.eq(Coupon::getStatus, status);
        wrapper.orderByDesc(Coupon::getCreateTime);
        return couponMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
