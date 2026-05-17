package com.orange.logistics.dispatch.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.dispatch.entity.DeliveryTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryTaskMapper extends BaseMapper<DeliveryTask> {
}
