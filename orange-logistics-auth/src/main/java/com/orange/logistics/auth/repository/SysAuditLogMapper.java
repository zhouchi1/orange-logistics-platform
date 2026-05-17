package com.orange.logistics.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.auth.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
