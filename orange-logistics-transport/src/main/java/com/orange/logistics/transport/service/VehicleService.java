package com.orange.logistics.transport.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.VehicleDTO;
import com.orange.logistics.transport.entity.Vehicle;

public interface VehicleService {
    Vehicle addVehicle(VehicleDTO dto);
    Vehicle updateVehicle(Long id, VehicleDTO dto);
    Vehicle getById(Long id);
    Vehicle getByPlateNumber(String plateNumber);
    Page<Vehicle> pageVehicles(Integer status, String fleetName, int page, int size);
    void updateStatus(Long id, Integer status);
    void updateLocation(Long id, java.math.BigDecimal longitude, java.math.BigDecimal latitude, String location);
    void deleteVehicle(Long id);
}
