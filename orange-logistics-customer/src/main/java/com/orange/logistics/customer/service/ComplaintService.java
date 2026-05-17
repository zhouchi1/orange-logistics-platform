package com.orange.logistics.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.customer.entity.Complaint;

public interface ComplaintService {
    Complaint createComplaint(Long customerId, String orderNo, Integer type, String title,
                              String description, String attachments);
    Complaint getById(Long id);
    Page<Complaint> pageComplaints(Long customerId, Integer status, int page, int size);
    void handleComplaint(Long id, String handler, String result);
    void closeComplaint(Long id);
    void rateSatisfaction(Long id, Integer score, String comment);
}
