package com.orange.logistics.dispatch.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.dispatch.entity.Courier;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourierMapper extends BaseMapper<Courier> {
}
