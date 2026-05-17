package com.orange.logistics.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {
}
