package com.orange.logistics.customer.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.logistics.customer.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AddressBookMapper extends BaseMapper<AddressBook> {
}
