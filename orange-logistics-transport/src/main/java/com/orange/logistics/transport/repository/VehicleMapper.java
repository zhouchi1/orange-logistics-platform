package com.orange.logistics.transport.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.transport.entity.Vehicle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VehicleMapper extends BaseMapper<Vehicle> {
}
