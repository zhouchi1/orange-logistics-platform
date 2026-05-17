package com.orange.logistics.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.customer.entity.Complaint;
import com.orange.logistics.customer.entity.Customer;
import com.orange.logistics.customer.repository.ComplaintMapper;
import com.orange.logistics.customer.repository.CustomerMapper;
import com.orange.logistics.customer.service.ComplaintService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintMapper complaintMapper;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public Complaint createComplaint(Long customerId, String orderNo, Integer type, String title,
                                     String description, String attachments) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new RuntimeException("客户不存在");

        Complaint complaint = new Complaint();
        complaint.setComplaintNo(generateComplaintNo());
        complaint.setCustomerId(customerId);
        complaint.setCustomerName(customer.getName());
        complaint.setCustomerPhone(customer.getPhone());
        complaint.setOrderNo(orderNo);
        complaint.setType(type);
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setAttachments(attachments);
        complaint.setStatus(1); // 待处        complaint.setDeleted(0);
        complaint.setCreateTime(LocalDateTime.now());
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.insert(complaint);
        log.info("创建投诉工单: {} 客户={}", complaint.getComplaintNo(), customer.getName());
        return complaint;
    }

    @Override
    public Complaint getById(Long id) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) throw new RuntimeException("工单不存在");
        return complaint;
    }

    @Override
    public Page<Complaint> pageComplaints(Long customerId, Integer status, int page, int size) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(Complaint::getCustomerId, customerId);
        if (status != null) wrapper.eq(Complaint::getStatus, status);
        wrapper.orderByDesc(Complaint::getCreateTime);
        return complaintMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void handleComplaint(Long id, String handler, String result) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) throw new RuntimeException("工单不存在");
        complaint.setStatus(3); // 已解        complaint.setHandler(handler);
        complaint.setHandleResult(result);
        complaint.setHandleTime(LocalDateTime.now());
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.updateById(complaint);
        log.info("处理投诉工单: {} 处理器{}", complaint.getComplaintNo(), handler);
    }

    @Override
    @Transactional
    public void closeComplaint(Long id) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) throw new RuntimeException("工单不存在");
        complaint.setStatus(4); // 已关        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.updateById(complaint);
    }

    @Override
    @Transactional
    public void rateSatisfaction(Long id, Integer score, String comment) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) throw new RuntimeException("工单不存在");
        if (complaint.getStatus() < 3) throw new RuntimeException("工单尚未处理完成");
        complaint.setSatisfaction(score);
        complaint.setSatisfactionComment(comment);
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.updateById(complaint);
        log.info("满意度评 工单={} 评分={}", complaint.getComplaintNo(), score);
    }

    private String generateComplaintNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "TS" + date + snowflake.substring(snowflake.length() - 8);
    }
}
