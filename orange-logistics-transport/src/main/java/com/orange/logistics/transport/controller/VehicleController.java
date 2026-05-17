package com.orange.logistics.transport.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.VehicleDTO;
import com.orange.logistics.transport.entity.Vehicle;
import com.orange.logistics.transport.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "车辆管理", description = "车辆CRUD、状态管理、位置更新")
@RestController
@RequestMapping("/api/transport/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "新增车辆")
    @PostMapping
    public ResponseEntity<Vehicle> addVehicle(@Valid @RequestBody VehicleDTO dto) {
        return ResponseEntity.ok(vehicleService.addVehicle(dto));
    }

    @Operation(summary = "更新车辆信息")
    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleDTO dto) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, dto));
    }

    @Operation(summary = "根据ID查询车辆")
    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getById(id));
    }

    @Operation(summary = "根据车牌号查询")
    @GetMapping("/plate/{plateNumber}")
    public ResponseEntity<Vehicle> getByPlateNumber(@PathVariable String plateNumber) {
        return ResponseEntity.ok(vehicleService.getByPlateNumber(plateNumber));
    }

    @Operation(summary = "分页查询车辆")
    @GetMapping("/page")
    public ResponseEntity<Page<Vehicle>> pageVehicles(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String fleetName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(vehicleService.pageVehicles(status, fleetName, page, size));
    }

    @Operation(summary = "更新车辆状态")
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        vehicleService.updateStatus(id, status);
        return ResponseEntity.ok("状态更新成功");
    }

    @Operation(summary = "删除车辆")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.ok("删除成功");
    }
}
