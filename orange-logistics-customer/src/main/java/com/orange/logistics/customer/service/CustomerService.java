package com.orange.logistics.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.customer.entity.Customer;

public interface CustomerService {
    Customer register(String name, String phone, String email);
    Customer getById(Long id);
    Customer getByPhone(String phone);
    Page<Customer> pageCustomers(Integer level, Integer status, int page, int size);
    void updateCustomer(Long id, String name, String email);
    void addPoints(Long id, Integer points);
    void upgradeLevel(Long id);
    void freeze(Long id);
    void unfreeze(Long id);
}
