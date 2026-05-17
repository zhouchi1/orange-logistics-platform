package com.orange.logistics.waybill.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.waybill.entity.Waybill;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaybillMapper extends BaseMapper<Waybill> {
}
