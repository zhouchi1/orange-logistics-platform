package com.orange.logistics.transport.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.transport.entity.TemperatureRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TemperatureRecordMapper extends BaseMapper<TemperatureRecord> {
}
