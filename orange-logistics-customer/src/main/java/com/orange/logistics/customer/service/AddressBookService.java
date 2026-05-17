package com.orange.logistics.customer.service;

import com.orange.logistics.customer.entity.AddressBook;
import java.util.List;

public interface AddressBookService {
    AddressBook addAddress(Long customerId, String contactName, String contactPhone,
                           String province, String city, String district, String street,
                           String detailAddress, String tag, Boolean isDefault);
    AddressBook updateAddress(Long id, String contactName, String contactPhone,
                              String province, String city, String district, String street,
                              String detailAddress, String tag);
    List<AddressBook> getByCustomerId(Long customerId);
    void setDefault(Long customerId, Long addressId);
    void deleteAddress(Long id);
}
