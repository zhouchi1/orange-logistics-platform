package com.orange.logistics.dispatch.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.DispatchRequestDTO;
import com.orange.logistics.dispatch.dto.SignoffDTO;
import com.orange.logistics.dispatch.entity.DeliveryTask;

public interface DispatchService {
    DeliveryTask dispatch(DispatchRequestDTO dto);
    DeliveryTask manualAssign(Long taskId, Long courierId);
    void pickup(Long taskId);
    void signoff(SignoffDTO dto);
    DeliveryTask getById(Long id);
    Page<DeliveryTask> pageTasks(Long courierId, Integer status, int page, int size);
    void changeDeliveryMethod(Long taskId, Integer method, String deliveryPoint);
}
