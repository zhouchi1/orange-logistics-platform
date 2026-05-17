package com.orange.logistics.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.customer.entity.Customer;
import com.orange.logistics.customer.repository.CustomerMapper;
import com.orange.logistics.customer.service.CustomerService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public Customer register(String name, String phone, String email) {
        Customer existing = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>().eq(Customer::getPhone, phone));
        if (existing != null) {
            throw new RuntimeException("手机号已注册");
        }

        Customer customer = new Customer();
        customer.setCustomerCode("C" + IdUtil.getSnowflakeNextId());
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setLevel(1);
        customer.setPoints(0);
        customer.setTotalOrders(0);
        customer.setStatus(1);
        customer.setDeleted(0);
        customer.setCreateTime(LocalDateTime.now());
        customer.setUpdateTime(LocalDateTime.now());
        customerMapper.insert(customer);
        log.info("客户注册: {} - {}", customer.getCustomerCode(), name);
        return customer;
    }

    @Override
    public Customer getById(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        return customer;
    }

    @Override
    public Customer getByPhone(String phone) {
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>().eq(Customer::getPhone, phone));
        if (customer == null) throw new RuntimeException("客户不存在");
        return customer;
    }

    @Override
    public Page<Customer> pageCustomers(Integer level, Integer status, int page, int size) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (level != null) wrapper.eq(Customer::getLevel, level);
        if (status != null) wrapper.eq(Customer::getStatus, status);
        wrapper.orderByDesc(Customer::getCreateTime);
        return customerMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void updateCustomer(Long id, String name, String email) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        if (name != null) customer.setName(name);
        if (email != null) customer.setEmail(email);
        customer.setUpdateTime(LocalDateTime.now());
        customerMapper.updateById(customer);
    }

    @Override
    @Transactional
    public void addPoints(Long id, Integer points) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        customer.setPoints(customer.getPoints() + points);
        customer.setUpdateTime(LocalDateTime.now());
        customerMapper.updateById(customer);
        log.info("客户积分增加: {} +{} = {}", customer.getName(), points, customer.getPoints());
    }

    @Override
    @Transactional
    public void upgradeLevel(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        // 根据积分自动升级
        int newLevel;
        if (customer.getPoints() >= 10000) newLevel = 4; // 钻石
        else if (customer.getPoints() >= 5000) newLevel = 3; // 金牌
        else if (customer.getPoints() >= 1000) newLevel = 2; // 银牌
        else newLevel = 1;

        if (newLevel > customer.getLevel()) {
            customer.setLevel(newLevel);
            customer.setUpdateTime(LocalDateTime.now());
            customerMapper.updateById(customer);
            log.info("客户升级: {} -> 等级{}", customer.getName(), newLevel);
        }
    }

    @Override
    @Transactional
    public void freeze(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        customer.setStatus(2);
        customer.setUpdateTime(LocalDateTime.now());
        customerMapper.updateById(customer);
        log.info("客户冻结: {}", customer.getName());
    }

    @Override
    @Transactional
    public void unfreeze(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) throw new RuntimeException("客户不存在");
        customer.setStatus(1);
        customer.setUpdateTime(LocalDateTime.now());
        customerMapper.updateById(customer);
        log.info("客户解冻: {}", customer.getName());
    }
}
