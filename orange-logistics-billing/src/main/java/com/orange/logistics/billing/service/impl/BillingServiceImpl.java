package com.orange.logistics.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.billing.dto.FreightCalcDTO;
import com.orange.logistics.billing.dto.FreightCalcResult;
import com.orange.logistics.billing.entity.Bill;
import com.orange.logistics.billing.entity.Coupon;
import com.orange.logistics.billing.entity.FreightRule;
import com.orange.logistics.billing.repository.BillMapper;
import com.orange.logistics.billing.repository.CouponMapper;
import com.orange.logistics.billing.repository.FreightRuleMapper;
import com.orange.logistics.billing.service.BillingService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillMapper billMapper;
    private final FreightRuleMapper ruleMapper;
    private final CouponMapper couponMapper;

    /**
     * 运费计算引擎
     * 公式: 首重价格 + (重量-首重)/续重单位 * 续重单价 + 距离*距离附加费 + 时效加价
     */
    @Override
    public FreightCalcResult calculateFreight(FreightCalcDTO dto) {
        // 查找适用的计费规则
        FreightRule rule = findApplicableRule(dto);
        if (rule == null) {
            // 使用默认规则
            rule = getDefaultRule();
        }

        FreightCalcResult result = new FreightCalcResult();

        // 1. 首重费用
        BigDecimal baseFreight = rule.getFirstPrice();
        result.setBaseFreight(baseFreight);

        // 2. 续重费用: (重量 - 首重) / 续重单位 * 续重单价
        BigDecimal weightFreight = BigDecimal.ZERO;
        if (dto.getWeight().compareTo(rule.getFirstWeight()) > 0) {
            BigDecimal extraWeight = dto.getWeight().subtract(rule.getFirstWeight());
            BigDecimal units = extraWeight.divide(rule.getAdditionalWeight(), 0, RoundingMode.CEILING);
            weightFreight = units.multiply(rule.getAdditionalPrice());
        }
        result.setWeightFreight(weightFreight);

        // 3. 距离附加费
        BigDecimal distanceSurcharge = BigDecimal.ZERO;
        if (dto.getDistance() != null && rule.getDistanceSurcharge() != null) {
            distanceSurcharge = dto.getDistance().multiply(rule.getDistanceSurcharge())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        result.setDistanceSurcharge(distanceSurcharge);

        // 4. 时效加价
        BigDecimal serviceMarkup = BigDecimal.ZERO;
        BigDecimal subtotal = baseFreight.add(weightFreight).add(distanceSurcharge);
        if (dto.getServiceType() != null) {
            if (dto.getServiceType() == 2 && rule.getExpressMarkup() != null) {
                serviceMarkup = subtotal.multiply(rule.getExpressMarkup()).setScale(2, RoundingMode.HALF_UP);
            } else if (dto.getServiceType() == 3 && rule.getSameDayMarkup() != null) {
                serviceMarkup = subtotal.multiply(rule.getSameDayMarkup()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        result.setServiceMarkup(serviceMarkup);

        // 5. 总运费
        BigDecimal totalFreight = subtotal.add(serviceMarkup);
        // 确保不低于最低运费
        if (rule.getMinFreight() != null && totalFreight.compareTo(rule.getMinFreight()) < 0) {
            totalFreight = rule.getMinFreight();
        }
        result.setTotalFreight(totalFreight);

        // 6. 优惠券抵扣
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (dto.getCouponId() != null) {
            Coupon coupon = couponMapper.selectById(dto.getCouponId());
            if (coupon != null && coupon.getStatus() == 1) {
                couponDiscount = calculateCouponDiscount(coupon, totalFreight);
            }
        }
        result.setCouponDiscount(couponDiscount);
        result.setActualAmount(totalFreight.subtract(couponDiscount).max(BigDecimal.ZERO));
        result.setRuleApplied(rule.getRuleName());

        log.info("运费计算: 重量={}kg 距离={}km 服务类型={} 总运费={} 实付={}",
                dto.getWeight(), dto.getDistance(), dto.getServiceType(),
                result.getTotalFreight(), result.getActualAmount());
        return result;
    }

    @Override
    public Map<String, Object> calculateFreightMap(Map<String, Object> params) {
        FreightCalcDTO dto = new FreightCalcDTO();
        dto.setWeight(params.get("weight") != null ? new BigDecimal(params.get("weight").toString()) : BigDecimal.ONE);
        dto.setVolume(params.get("volume") != null ? new BigDecimal(params.get("volume").toString()) : null);
        dto.setSenderCity(params.get("senderCity") != null ? params.get("senderCity").toString() : null);
        dto.setReceiverCity(params.get("receiverCity") != null ? params.get("receiverCity").toString() : null);
        dto.setDistance(params.get("distance") != null ? new BigDecimal(params.get("distance").toString()) : null);
        dto.setServiceType(params.get("serviceType") != null ? Integer.parseInt(params.get("serviceType").toString()) : 1);

        FreightCalcResult result = calculateFreight(dto);
        Map<String, Object> response = new HashMap<>();
        response.put("freight", result.getTotalFreight());
        response.put("actualAmount", result.getActualAmount());
        response.put("detail", result);
        return response;
    }

    @Override
    @Transactional
    public Bill createBill(String orderNo, String waybillNo, Long customerId, FreightCalcDTO calcDto) {
        FreightCalcResult calcResult = calculateFreight(calcDto);

        Bill bill = new Bill();
        bill.setBillNo(generateBillNo());
        bill.setOrderNo(orderNo);
        bill.setWaybillNo(waybillNo);
        bill.setCustomerId(customerId);
        bill.setWeight(calcDto.getWeight());
        bill.setVolume(calcDto.getVolume());
        bill.setDistance(calcDto.getDistance());
        bill.setServiceType(calcDto.getServiceType());
        bill.setBaseFreight(calcResult.getBaseFreight());
        bill.setWeightFreight(calcResult.getWeightFreight());
        bill.setDistanceSurcharge(calcResult.getDistanceSurcharge());
        bill.setServiceMarkup(calcResult.getServiceMarkup());
        bill.setCouponDiscount(calcResult.getCouponDiscount());
        bill.setCouponId(calcDto.getCouponId());
        bill.setTotalFreight(calcResult.getTotalFreight());
        bill.setActualAmount(calcResult.getActualAmount());
        bill.setPaymentStatus(1); // 待支付
        bill.setSettlementStatus(1); // 未结算
        bill.setDeleted(0);
        bill.setCreateTime(LocalDateTime.now());
        bill.setUpdateTime(LocalDateTime.now());
        billMapper.insert(bill);

        // 使用优惠券
        if (calcDto.getCouponId() != null) {
            Coupon coupon = couponMapper.selectById(calcDto.getCouponId());
            if (coupon != null && coupon.getStatus() == 1) {
                coupon.setStatus(2);
                coupon.setUseTime(LocalDateTime.now());
                coupon.setOrderNo(orderNo);
                coupon.setUpdateTime(LocalDateTime.now());
                couponMapper.updateById(coupon);
            }
        }

        log.info("创建账单: {} 订单={} 金额={}", bill.getBillNo(), orderNo, bill.getActualAmount());
        return bill;
    }

    @Override
    public Bill getById(Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) throw new RuntimeException("账单不存在");
        return bill;
    }

    @Override
    public Bill getByBillNo(String billNo) {
        Bill bill = billMapper.selectOne(new LambdaQueryWrapper<Bill>().eq(Bill::getBillNo, billNo));
        if (bill == null) throw new RuntimeException("账单不存在");
        return bill;
    }

    @Override
    public Page<Bill> pageBills(Long customerId, Integer paymentStatus, int page, int size) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(Bill::getCustomerId, customerId);
        if (paymentStatus != null) wrapper.eq(Bill::getPaymentStatus, paymentStatus);
        wrapper.orderByDesc(Bill::getCreateTime);
        return billMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void payBill(Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) throw new RuntimeException("账单不存在");
        if (bill.getPaymentStatus() != 1) throw new RuntimeException("账单状态不允许支付");
        bill.setPaymentStatus(2);
        bill.setPayTime(LocalDateTime.now());
        bill.setUpdateTime(LocalDateTime.now());
        billMapper.updateById(bill);
        log.info("账单支付成功: {}", bill.getBillNo());
    }

    @Override
    @Transactional
    public void refundBill(Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) throw new RuntimeException("账单不存在");
        if (bill.getPaymentStatus() != 2) throw new RuntimeException("只有已支付的账单才能退款");
        bill.setPaymentStatus(3);
        bill.setUpdateTime(LocalDateTime.now());
        billMapper.updateById(bill);

        // 退还优惠券
        if (bill.getCouponId() != null) {
            Coupon coupon = couponMapper.selectById(bill.getCouponId());
            if (coupon != null) {
                coupon.setStatus(1);
                coupon.setUseTime(null);
                coupon.setOrderNo(null);
                coupon.setUpdateTime(LocalDateTime.now());
                couponMapper.updateById(coupon);
            }
        }
        log.info("账单退款成功: {}", bill.getBillNo());
    }

    private FreightRule findApplicableRule(FreightCalcDTO dto) {
        LambdaQueryWrapper<FreightRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FreightRule::getStatus, 1);
        if (dto.getServiceType() != null) {
            wrapper.and(w -> w.eq(FreightRule::getServiceType, dto.getServiceType())
                    .or().eq(FreightRule::getServiceType, 0));
        }
        if (dto.getSenderCity() != null) {
            wrapper.and(w -> w.eq(FreightRule::getOriginRegion, dto.getSenderCity())
                    .or().isNull(FreightRule::getOriginRegion));
        }
        wrapper.last("LIMIT 1");
        return ruleMapper.selectOne(wrapper);
    }

    private FreightRule getDefaultRule() {
        FreightRule rule = new FreightRule();
        rule.setRuleName("默认规则");
        rule.setFirstWeight(BigDecimal.ONE);
        rule.setFirstPrice(BigDecimal.valueOf(12));
        rule.setAdditionalWeight(BigDecimal.ONE);
        rule.setAdditionalPrice(BigDecimal.valueOf(5));
        rule.setDistanceSurcharge(BigDecimal.valueOf(0.02));
        rule.setExpressMarkup(BigDecimal.valueOf(0.5));
        rule.setSameDayMarkup(BigDecimal.ONE);
        rule.setMinFreight(BigDecimal.valueOf(8));
        return rule;
    }

    private BigDecimal calculateCouponDiscount(Coupon coupon, BigDecimal amount) {
        if (coupon.getThreshold() != null && amount.compareTo(coupon.getThreshold()) < 0) {
            return BigDecimal.ZERO; // 未达到使用门槛
        }
        switch (coupon.getCouponType()) {
            case 1: // 满减
                return coupon.getDiscount();
            case 2: // 折扣
                BigDecimal discount = amount.multiply(BigDecimal.ONE.subtract(coupon.getDiscount()))
                        .setScale(2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscount() != null) {
                    return discount.min(coupon.getMaxDiscount());
                }
                return discount;
            case 3: // 免邮
                return amount;
            default:
                return BigDecimal.ZERO;
        }
    }

    private String generateBillNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "BL" + date + snowflake.substring(snowflake.length() - 8);
    }
}
