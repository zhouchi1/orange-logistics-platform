package com.orange.logistics.customer.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.customer.entity.Complaint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {
}
