package com.orange.logistics.transport.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.transport.entity.TransportRoute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransportRouteMapper extends BaseMapper<TransportRoute> {
}
