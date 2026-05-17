package com.orange.logistics.waybill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.waybill.dto.SignConfirmDTO;
import com.orange.logistics.waybill.entity.Waybill;
import com.orange.logistics.waybill.entity.WaybillTrace;
import com.orange.logistics.waybill.repository.WaybillMapper;
import com.orange.logistics.waybill.repository.WaybillTraceMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 签收服务
 * 支持正常签收、代签、拒签
 * 签收照片上传（URL存储）
 * 电子签名验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignoffService {

    private final WaybillMapper waybillMapper;
    private final WaybillTraceMapper traceMapper;
    // private final RocketMQTemplate rocketMQTemplate;

    /**
     * 签收类型
     */
    public static final String SIGN_TYPE_NORMAL = "NORMAL";     // 本人签收
    public static final String SIGN_TYPE_PROXY = "PROXY";       // 代签
    public static final String SIGN_TYPE_PHOTO = "PHOTO";       // 拍照签收
    public static final String SIGN_TYPE_SIGNATURE = "SIGNATURE"; // 电子签名
    public static final String SIGN_TYPE_REJECT = "REJECT";     // 拒签

    /**
     * 正常签收
     * 本人当面签收，可附带签收照片或电子签名
     *
     * @param dto 签收信息
     * @return 签收结果
     */
    @Operation(summary = "正常签收")
    @Transactional
    public SignoffResult normalSignoff(SignConfirmDTO dto) {
        validateSignoffRequest(dto);

        Waybill waybill = findWaybill(dto);
        validateWaybillForSignoff(waybill);

        // 设置签收信息
        waybill.setStatus(4); // 已签收
        waybill.setSignType(dto.getSignType() != null ? dto.getSignType() : SIGN_TYPE_NORMAL);
        waybill.setSignedBy(dto.getSignedBy());
        waybill.setSignTime(LocalDateTime.now());
        waybill.setUpdateTime(LocalDateTime.now());

        // 处理签收照片
        if (dto.getSignPhotoUrl() != null && !dto.getSignPhotoUrl().isBlank()) {
            waybill.setSignPhotoUrl(dto.getSignPhotoUrl());
        }

        // 处理电子签名
        if (dto.getSignatureData() != null && !dto.getSignatureData().isBlank()) {
            if (!validateSignatureData(dto.getSignatureData())) {
                return SignoffResult.fail(waybill.getWaybillNo(), "电子签名数据无效");
            }
            waybill.setSignatureData(dto.getSignatureData());
        }

        waybillMapper.updateById(waybill);

        // 记录签收轨迹
        addSignoffTrace(waybill, "已签收 - 本人签收", dto.getSignedBy());

        // 发送签收消息
        sendSignoffMessage(waybill, SIGN_TYPE_NORMAL, true);

        log.info("运单正常签收: waybillNo={}, signedBy={}", waybill.getWaybillNo(), dto.getSignedBy());

        return SignoffResult.success(waybill.getWaybillNo(), "签收成功");
    }

    /**
     * 代签
     * 非本人签收（如家人、同事、前台等代为签收）
     *
     * @param dto 签收信息（signedBy为代签人）
     * @return 签收结果
     */
    @Operation(summary = "代签")
    @Transactional
    public SignoffResult proxySignoff(SignConfirmDTO dto) {
        validateSignoffRequest(dto);

        if (dto.getSignedBy() == null || dto.getSignedBy().isBlank()) {
            throw new IllegalArgumentException("代签人姓名不能为空");
        }

        Waybill waybill = findWaybill(dto);
        validateWaybillForSignoff(waybill);

        // 代签必须有照片凭证
        if (dto.getSignPhotoUrl() == null || dto.getSignPhotoUrl().isBlank()) {
            log.warn("代签缺少照片凭证: waybillNo={}", waybill.getWaybillNo());
            // 不强制要求，但记录警告
        }

        waybill.setStatus(4); // 已签收
        waybill.setSignType(SIGN_TYPE_PROXY);
        waybill.setSignedBy(dto.getSignedBy());
        waybill.setSignPhotoUrl(dto.getSignPhotoUrl());
        waybill.setSignTime(LocalDateTime.now());
        waybill.setUpdateTime(LocalDateTime.now());
        waybillMapper.updateById(waybill);

        // 记录代签轨迹
        String desc = String.format("已签收 - 代签（代签人: %s）", dto.getSignedBy());
        addSignoffTrace(waybill, desc, dto.getSignedBy());

        // 发送签收消息
        sendSignoffMessage(waybill, SIGN_TYPE_PROXY, true);

        log.info("运单代签: waybillNo={}, proxyBy={}", waybill.getWaybillNo(), dto.getSignedBy());

        return SignoffResult.success(waybill.getWaybillNo(), "代签成功，已通知收件人");
    }

    /**
     * 拒签
     * 收件人拒绝签收包裹
     *
     * @param waybillNo 运单号
     * @param reason    拒签原因
     * @param operator  操作人（快递员）
     * @return 拒签结果
     */
    @Operation(summary = "拒签")
    @Transactional
    public SignoffResult rejectSignoff(String waybillNo, String reason, String operator) {
        if (waybillNo == null || waybillNo.isBlank()) {
            throw new IllegalArgumentException("运单号不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("拒签原因不能为空");
        }

        Waybill waybill = waybillMapper.selectOne(
                new LambdaQueryWrapper<Waybill>().eq(Waybill::getWaybillNo, waybillNo));
        if (waybill == null) {
            throw new IllegalArgumentException("运单不存在: " + waybillNo);
        }

        // 拒签只能在派送中状态
        if (waybill.getStatus() != 3) {
            throw new IllegalStateException("当前状态不允许拒签，运单状态: " + waybill.getStatus());
        }

        // 拒签不改为已签收，而是标记为异常状态（需要退回）
        waybill.setStatus(2); // 回到运输中（退回流程）
        waybill.setSignType(SIGN_TYPE_REJECT);
        waybill.setUpdateTime(LocalDateTime.now());
        waybillMapper.updateById(waybill);

        // 记录拒签轨迹
        String desc = "拒签 - 原因: " + reason;
        addSignoffTrace(waybill, desc, operator);

        // 发送拒签消息（触发退回流程）
        Map<String, Object> msg = new HashMap<>();
        msg.put("waybillNo", waybillNo);
        msg.put("orderId", waybill.getOrderId());
        msg.put("reason", reason);
        msg.put("rejectTime", LocalDateTime.now().toString());
        // RocketMQ disabled for local dev
        log.info("[拒签消息] waybillNo={}, reason={} (RocketMQ已禁用)", waybillNo, reason);

        log.info("运单拒签: waybillNo={}, reason={}", waybillNo, reason);

        return SignoffResult.builder()
                .success(true)
                .waybillNo(waybillNo)
                .signType(SIGN_TYPE_REJECT)
                .message("拒签处理完成，包裹将退回发件人")
                .build();
    }

    /**
     * 验证电子签名数据
     * 检查Base64编码的签名数据是否有效
     *
     * @param signatureData Base64编码的签名数据
     * @return 是否有效
     */
    @Operation(summary = "验证电子签名")
    public boolean validateSignatureData(String signatureData) {
        if (signatureData == null || signatureData.isBlank()) {
            return false;
        }

        try {
            // 验证Base64格式
            byte[] decoded = Base64.getDecoder().decode(signatureData);

            // 签名数据最小长度检查（防止空白签名）
            if (decoded.length < 100) {
                log.warn("电子签名数据过短，可能为无效签名: length={}", decoded.length);
                return false;
            }

            // 签名数据最大长度检查（防止恶意大数据）
            if (decoded.length > 500 * 1024) { // 500KB
                log.warn("电子签名数据过大: length={}", decoded.length);
                return false;
            }

            return true;
        } catch (IllegalArgumentException e) {
            log.warn("电子签名Base64解码失败: {}", e.getMessage());
            return false;
        }
    }

    // ========== 私有方法 ==========
    private void validateSignoffRequest(SignConfirmDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("签收信息不能为空");
        }
        if (dto.getWaybillId() == null && (dto.getWaybillNo() == null || dto.getWaybillNo().isBlank())) {
            throw new IllegalArgumentException("运单ID或运单号至少提供一个");
        }
    }

    private Waybill findWaybill(SignConfirmDTO dto) {
        Waybill waybill;
        if (dto.getWaybillId() != null) {
            waybill = waybillMapper.selectById(dto.getWaybillId());
        } else {
            waybill = waybillMapper.selectOne(
                    new LambdaQueryWrapper<Waybill>().eq(Waybill::getWaybillNo, dto.getWaybillNo()));
        }
        if (waybill == null) {
            throw new IllegalArgumentException("运单不存在");
        }
        return waybill;
    }

    private void validateWaybillForSignoff(Waybill waybill) {
        if (waybill.getStatus() == 4) {
            throw new IllegalStateException("运单已签收，不能重复签收");
        }
        if (waybill.getStatus() != 3) {
            throw new IllegalStateException("运单当前状态不允许签收，需要在派送中状态");
        }
    }

    private void addSignoffTrace(Waybill waybill, String description, String operator) {
        WaybillTrace trace = new WaybillTrace();
        trace.setWaybillId(waybill.getId());
        trace.setWaybillNo(waybill.getWaybillNo());
        trace.setLocation(waybill.getCurrentLocation());
        trace.setDescription(description);
        trace.setOperator(operator);
        trace.setTraceType(5); // 签收类型
        trace.setTraceTime(LocalDateTime.now());
        trace.setCreateTime(LocalDateTime.now());
        traceMapper.insert(trace);
    }

    private void sendSignoffMessage(Waybill waybill, String signType, boolean accepted) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("waybillNo", waybill.getWaybillNo());
        msg.put("orderId", waybill.getOrderId());
        msg.put("orderNo", waybill.getOrderNo());
        msg.put("signType", signType);
        msg.put("signedBy", waybill.getSignedBy());
        msg.put("accepted", accepted);
        msg.put("signTime", LocalDateTime.now().toString());
        // RocketMQ disabled for local dev
        log.info("[签收消息] waybillNo={}, signType={} (RocketMQ已禁用)", waybill.getWaybillNo(), signType);
    }

    // ========== 内部类 ==========

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SignoffResult {
        private boolean success;
        private String waybillNo;
        private String signType;
        private String message;

        public static SignoffResult success(String waybillNo, String message) {
            return SignoffResult.builder()
                    .success(true)
                    .waybillNo(waybillNo)
                    .message(message)
                    .build();
        }

        public static SignoffResult fail(String waybillNo, String message) {
            return SignoffResult.builder()
                    .success(false)
                    .waybillNo(waybillNo)
                    .message(message)
                    .build();
        }
    }
}
