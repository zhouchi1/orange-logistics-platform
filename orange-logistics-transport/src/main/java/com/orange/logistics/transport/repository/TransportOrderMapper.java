package com.orange.logistics.transport.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.transport.entity.TransportOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransportOrderMapper extends BaseMapper<TransportOrder> {
}
