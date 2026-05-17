package com.orange.logistics.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.order.entity.LogisticsOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<LogisticsOrder> {
}
