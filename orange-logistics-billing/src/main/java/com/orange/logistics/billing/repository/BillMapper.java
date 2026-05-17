package com.orange.logistics.billing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.billing.entity.Bill;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {
}
