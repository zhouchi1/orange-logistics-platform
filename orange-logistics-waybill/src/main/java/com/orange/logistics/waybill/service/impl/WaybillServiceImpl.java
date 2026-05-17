package com.orange.logistics.waybill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.waybill.dto.AddTraceDTO;
import com.orange.logistics.waybill.dto.GenerateWaybillDTO;
import com.orange.logistics.waybill.dto.SignConfirmDTO;
import com.orange.logistics.waybill.entity.Waybill;
import com.orange.logistics.waybill.entity.WaybillTrace;
import com.orange.logistics.waybill.repository.WaybillMapper;
import com.orange.logistics.waybill.repository.WaybillTraceMapper;
import com.orange.logistics.waybill.service.WaybillService;
import com.orange.logistics.waybill.vo.WaybillTraceVO;
import com.orange.logistics.waybill.vo.WaybillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaybillServiceImpl implements WaybillService {

    private final WaybillMapper waybillMapper;
    private final WaybillTraceMapper traceMapper;
    // private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final String WAYBILL_SEQ_KEY = "waybill:seq:";
    private static final String[] TRACE_TYPE_DESC = {"", "揽收", "到达", "发出", "派件", "签收", "异常"};

    @Override
    @Transactional
    public WaybillVO generateWaybill(GenerateWaybillDTO dto) {
        String waybillNo = generateWaybillNo();

        Waybill waybill = new Waybill();
        waybill.setWaybillNo(waybillNo);
        waybill.setOrderId(dto.getOrderId());
        waybill.setOrderNo(dto.getOrderNo());
        waybill.setSenderName(dto.getSenderName());
        waybill.setSenderPhone(dto.getSenderPhone());
        waybill.setSenderAddress(dto.getSenderAddress());
        waybill.setReceiverName(dto.getReceiverName());
        waybill.setReceiverPhone(dto.getReceiverPhone());
        waybill.setReceiverAddress(dto.getReceiverAddress());
        waybill.setWeight(dto.getWeight());
        waybill.setVolume(dto.getVolume());
        waybill.setItemCount(dto.getItemCount());
        waybill.setItemDescription(dto.getItemDescription());
        waybill.setStatus(0); // 待揽收
        waybill.setDeleted(0);
        waybill.setCreateTime(LocalDateTime.now());
        waybill.setUpdateTime(LocalDateTime.now());
        waybillMapper.insert(waybill);

        log.info("运单生成成功: {}", waybillNo);
        return convertToVO(waybill);
    }

    @Override
    public Map<String, Object> generateWaybillFromMap(Map<String, Object> params) {
        GenerateWaybillDTO dto = new GenerateWaybillDTO();
        dto.setOrderId(Long.parseLong(params.getOrDefault("orderId", "0").toString()));
        dto.setOrderNo((String) params.get("orderNo"));
        dto.setSenderName((String) params.get("senderName"));
        dto.setSenderPhone((String) params.get("senderPhone"));
        dto.setSenderAddress((String) params.get("senderAddress"));
        dto.setReceiverName((String) params.get("receiverName"));
        dto.setReceiverPhone((String) params.get("receiverPhone"));
        dto.setReceiverAddress((String) params.get("receiverAddress"));

        WaybillVO vo = generateWaybill(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("waybillNo", vo.getWaybillNo());
        result.put("waybillId", vo.getId());
        return result;
    }

    @Override
    public WaybillVO getByWaybillNo(String waybillNo) {
        Waybill waybill = waybillMapper.selectOne(
                new LambdaQueryWrapper<Waybill>().eq(Waybill::getWaybillNo, waybillNo)
        );
        if (waybill == null) {
            throw new RuntimeException("运单不存在: " + waybillNo);
        }
        WaybillVO vo = convertToVO(waybill);
        vo.setTraces(getTraces(waybill.getId(), waybillNo));
        return vo;
    }

    @Override
    public WaybillVO getById(Long id) {
        Waybill waybill = waybillMapper.selectById(id);
        if (waybill == null) {
            throw new RuntimeException("运单不存在");
        }
        WaybillVO vo = convertToVO(waybill);
        vo.setTraces(getTraces(waybill.getId(), waybill.getWaybillNo()));
        return vo;
    }

    @Override
    @Transactional
    public void addTrace(AddTraceDTO dto) {
        Waybill waybill = waybillMapper.selectOne(
                new LambdaQueryWrapper<Waybill>().eq(Waybill::getWaybillNo, dto.getWaybillNo())
        );
        if (waybill == null) {
            throw new RuntimeException("运单不存在: " + dto.getWaybillNo());
        }

        WaybillTrace trace = new WaybillTrace();
        trace.setWaybillId(waybill.getId());
        trace.setWaybillNo(dto.getWaybillNo());
        trace.setLocation(dto.getLocation());
        trace.setDescription(dto.getDescription());
        trace.setOperator(dto.getOperator());
        trace.setTraceType(dto.getTraceType());
        trace.setLongitude(dto.getLongitude());
        trace.setLatitude(dto.getLatitude());
        trace.setTraceTime(dto.getTraceTime() != null ? dto.getTraceTime() : LocalDateTime.now());
        trace.setCreateTime(LocalDateTime.now());
        traceMapper.insert(trace);

        // 更新运单当前位置和状态
        waybill.setCurrentLocation(dto.getLocation());
        if (dto.getTraceType() != null) {
            switch (dto.getTraceType()) {
                case 1 -> waybill.setStatus(1); // 已揽收
                case 2, 3 -> waybill.setStatus(2); // 运输中
                case 4 -> waybill.setStatus(3); // 派送中
                case 5 -> waybill.setStatus(4); // 已签收
            }
        }
        waybill.setUpdateTime(LocalDateTime.now());
        waybillMapper.updateById(waybill);

        // RocketMQ disabled for local dev
        log.info("运单轨迹更新(RocketMQ已禁用): {} - {}", dto.getWaybillNo(), dto.getDescription());
    }

    @Override
    @Transactional
    public void confirmSign(SignConfirmDTO dto) {
        Waybill waybill;
        if (dto.getWaybillId() != null) {
            waybill = waybillMapper.selectById(dto.getWaybillId());
        } else {
            waybill = waybillMapper.selectOne(
                    new LambdaQueryWrapper<Waybill>().eq(Waybill::getWaybillNo, dto.getWaybillNo())
            );
        }
        if (waybill == null) {
            throw new RuntimeException("运单不存在");
        }

        waybill.setStatus(4); // 已签收
        waybill.setSignType(dto.getSignType());
        waybill.setSignPhotoUrl(dto.getSignPhotoUrl());
        waybill.setSignatureData(dto.getSignatureData());
        waybill.setSignedBy(dto.getSignedBy());
        waybill.setSignTime(LocalDateTime.now());
        waybill.setUpdateTime(LocalDateTime.now());
        waybillMapper.updateById(waybill);

        // 添加签收轨迹
        WaybillTrace trace = new WaybillTrace();
        trace.setWaybillId(waybill.getId());
        trace.setWaybillNo(waybill.getWaybillNo());
        trace.setLocation(waybill.getCurrentLocation());
        trace.setDescription("已签收 - " + dto.getSignType());
        trace.setOperator(dto.getSignedBy());
        trace.setTraceType(5);
        trace.setTraceTime(LocalDateTime.now());
        trace.setCreateTime(LocalDateTime.now());
        traceMapper.insert(trace);

        // RocketMQ disabled for local dev
        log.info("运单签收确认(RocketMQ已禁用): {} by {}", waybill.getWaybillNo(), dto.getSignedBy());
    }

    @Override
    public List<WaybillVO> getByOrderId(Long orderId) {
        List<Waybill> waybills = waybillMapper.selectList(
                new LambdaQueryWrapper<Waybill>().eq(Waybill::getOrderId, orderId)
        );
        return waybills.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 运单号生成规则: OG + 日期(8位) + 序列号(6位)
     * 例如: OG20260516000001
     */
    private String generateWaybillNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = WAYBILL_SEQ_KEY + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redisTemplate.expire(key, java.time.Duration.ofDays(2));
        }
        return String.format("OG%s%06d", date, seq);
    }

    private List<WaybillTraceVO> getTraces(Long waybillId, String waybillNo) {
        List<WaybillTrace> traces = traceMapper.selectList(
                new LambdaQueryWrapper<WaybillTrace>()
                        .eq(WaybillTrace::getWaybillId, waybillId)
                        .orderByDesc(WaybillTrace::getTraceTime)
        );
        return traces.stream().map(this::convertTraceToVO).collect(Collectors.toList());
    }

    private WaybillVO convertToVO(Waybill waybill) {
        WaybillVO vo = new WaybillVO();
        vo.setId(waybill.getId());
        vo.setWaybillNo(waybill.getWaybillNo());
        vo.setOrderId(waybill.getOrderId());
        vo.setOrderNo(waybill.getOrderNo());
        vo.setSenderName(waybill.getSenderName());
        vo.setSenderPhone(waybill.getSenderPhone());
        vo.setSenderAddress(waybill.getSenderAddress());
        vo.setReceiverName(waybill.getReceiverName());
        vo.setReceiverPhone(waybill.getReceiverPhone());
        vo.setReceiverAddress(waybill.getReceiverAddress());
        vo.setWeight(waybill.getWeight());
        vo.setStatus(waybill.getStatus());
        vo.setStatusDesc(getStatusDesc(waybill.getStatus()));
        vo.setSignType(waybill.getSignType());
        vo.setSignPhotoUrl(waybill.getSignPhotoUrl());
        vo.setSignedBy(waybill.getSignedBy());
        vo.setSignTime(waybill.getSignTime());
        vo.setCurrentLocation(waybill.getCurrentLocation());
        vo.setCreateTime(waybill.getCreateTime());
        return vo;
    }

    private WaybillTraceVO convertTraceToVO(WaybillTrace trace) {
        WaybillTraceVO vo = new WaybillTraceVO();
        vo.setLocation(trace.getLocation());
        vo.setDescription(trace.getDescription());
        vo.setOperator(trace.getOperator());
        vo.setTraceType(trace.getTraceType());
        if (trace.getTraceType() != null && trace.getTraceType() < TRACE_TYPE_DESC.length) {
            vo.setTraceTypeDesc(TRACE_TYPE_DESC[trace.getTraceType()]);
        }
        vo.setTraceTime(trace.getTraceTime());
        return vo;
    }

    private String getStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "待揽收";
            case 1 -> "已揽收";
            case 2 -> "运输中";
            case 3 -> "派送中";
            case 4 -> "已签收";
            default -> "未知";
        };
    }
}
