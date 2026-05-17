package com.orange.logistics.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.auth.entity.SysApiKey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysApiKeyMapper extends BaseMapper<SysApiKey> {
}
