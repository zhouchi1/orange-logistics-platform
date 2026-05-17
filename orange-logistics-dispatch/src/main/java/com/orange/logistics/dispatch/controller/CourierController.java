package com.orange.logistics.dispatch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.CourierDTO;
import com.orange.logistics.dispatch.entity.Courier;
import com.orange.logistics.dispatch.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "快递员管理", description = "快递员CRUD、状态管理、位置更新")
@RestController
@RequestMapping("/api/dispatch/courier")
@RequiredArgsConstructor
public class CourierController {

    private final CourierService courierService;

    @Operation(summary = "新增快递员")
    @PostMapping
    public ResponseEntity<Courier> addCourier(@Valid @RequestBody CourierDTO dto) {
        return ResponseEntity.ok(courierService.addCourier(dto));
    }

    @Operation(summary = "更新快递员信息")
    @PutMapping("/{id}")
    public ResponseEntity<Courier> updateCourier(@PathVariable Long id, @Valid @RequestBody CourierDTO dto) {
        return ResponseEntity.ok(courierService.updateCourier(id, dto));
    }

    @Operation(summary = "查询快递员详情")
    @GetMapping("/{id}")
    public ResponseEntity<Courier> getById(@PathVariable Long id) {
        return ResponseEntity.ok(courierService.getById(id));
    }

    @Operation(summary = "分页查询快递员")
    @GetMapping("/page")
    public ResponseEntity<Page<Courier>> pageCouriers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long stationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(courierService.pageCouriers(status, stationId, page, size));
    }

    @Operation(summary = "更新快递员状态")
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        courierService.updateStatus(id, status);
        return ResponseEntity.ok("状态更新成功");
    }

    @Operation(summary = "更新快递员位置")
    @PutMapping("/{id}/location")
    public ResponseEntity<String> updateLocation(
            @PathVariable Long id,
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude) {
        courierService.updateLocation(id, longitude, latitude);
        return ResponseEntity.ok("位置更新成功");
    }

    @Operation(summary = "删除快递员")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCourier(@PathVariable Long id) {
        courierService.deleteCourier(id);
        return ResponseEntity.ok("删除成功");
    }
}
