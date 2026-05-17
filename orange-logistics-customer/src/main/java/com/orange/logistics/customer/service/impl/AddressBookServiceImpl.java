package com.orange.logistics.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.customer.entity.AddressBook;
import com.orange.logistics.customer.repository.AddressBookMapper;
import com.orange.logistics.customer.service.AddressBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {

    private final AddressBookMapper addressBookMapper;

    @Override
    @Transactional
    public AddressBook addAddress(Long customerId, String contactName, String contactPhone,
                                  String province, String city, String district, String street,
                                  String detailAddress, String tag, Boolean isDefault) {
        AddressBook address = new AddressBook();
        address.setCustomerId(customerId);
        address.setContactName(contactName);
        address.setContactPhone(contactPhone);
        address.setProvince(province);
        address.setCity(city);
        address.setDistrict(district);
        address.setStreet(street);
        address.setDetailAddress(detailAddress);
        address.setFullAddress(province + city + district + (street != null ? street : "") + detailAddress);
        address.setTag(tag);
        address.setIsDefault(isDefault != null && isDefault);
        address.setDeleted(0);
        address.setCreateTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());

        // 如果设为默认，取消其他默认地址
        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultAddress(customerId);
        }

        addressBookMapper.insert(address);
        return address;
    }

    @Override
    @Transactional
    public AddressBook updateAddress(Long id, String contactName, String contactPhone,
                                     String province, String city, String district, String street,
                                     String detailAddress, String tag) {
        AddressBook address = addressBookMapper.selectById(id);
        if (address == null) throw new RuntimeException("地址不存在");
        if (contactName != null) address.setContactName(contactName);
        if (contactPhone != null) address.setContactPhone(contactPhone);
        if (province != null) address.setProvince(province);
        if (city != null) address.setCity(city);
        if (district != null) address.setDistrict(district);
        if (street != null) address.setStreet(street);
        if (detailAddress != null) address.setDetailAddress(detailAddress);
        address.setFullAddress(address.getProvince() + address.getCity() + address.getDistrict()
                + (address.getStreet() != null ? address.getStreet() : "") + address.getDetailAddress());
        if (tag != null) address.setTag(tag);
        address.setUpdateTime(LocalDateTime.now());
        addressBookMapper.updateById(address);
        return address;
    }

    @Override
    public List<AddressBook> getByCustomerId(Long customerId) {
        return addressBookMapper.selectList(
                new LambdaQueryWrapper<AddressBook>()
                        .eq(AddressBook::getCustomerId, customerId)
                        .orderByDesc(AddressBook::getIsDefault)
                        .orderByDesc(AddressBook::getUpdateTime));
    }

    @Override
    @Transactional
    public void setDefault(Long customerId, Long addressId) {
        clearDefaultAddress(customerId);
        AddressBook address = addressBookMapper.selectById(addressId);
        if (address == null) throw new RuntimeException("地址不存在");
        address.setIsDefault(true);
        address.setUpdateTime(LocalDateTime.now());
        addressBookMapper.updateById(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long id) {
        addressBookMapper.deleteById(id);
    }

    private void clearDefaultAddress(Long customerId) {
        List<AddressBook> defaults = addressBookMapper.selectList(
                new LambdaQueryWrapper<AddressBook>()
                        .eq(AddressBook::getCustomerId, customerId)
                        .eq(AddressBook::getIsDefault, true));
        for (AddressBook addr : defaults) {
            addr.setIsDefault(false);
            addr.setUpdateTime(LocalDateTime.now());
            addressBookMapper.updateById(addr);
        }
    }
}
