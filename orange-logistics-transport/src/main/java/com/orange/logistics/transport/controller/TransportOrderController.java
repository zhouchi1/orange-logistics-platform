package com.orange.logistics.transport.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.CreateTransportOrderDTO;
import com.orange.logistics.transport.entity.TransportOrder;
import com.orange.logistics.transport.service.TransportOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "运输单管理", description = "运输单创建、发车、到达、异常处理")
@RestController
@RequestMapping("/api/transport/order")
@RequiredArgsConstructor
public class TransportOrderController {

    private final TransportOrderService transportOrderService;

    @Operation(summary = "创建运输单")
    @PostMapping
    public ResponseEntity<TransportOrder> createOrder(@Valid @RequestBody CreateTransportOrderDTO dto) {
        return ResponseEntity.ok(transportOrderService.createOrder(dto));
    }

    @Operation(summary = "查询运输单详情")
    @GetMapping("/{id}")
    public ResponseEntity<TransportOrder> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transportOrderService.getById(id));
    }

    @Operation(summary = "根据运输单号查询")
    @GetMapping("/no/{transportNo}")
    public ResponseEntity<TransportOrder> getByTransportNo(@PathVariable String transportNo) {
        return ResponseEntity.ok(transportOrderService.getByTransportNo(transportNo));
    }

    @Operation(summary = "分页查询运输单")
    @GetMapping("/page")
    public ResponseEntity<Page<TransportOrder>> pageOrders(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(transportOrderService.pageOrders(vehicleId, status, page, size));
    }

    @Operation(summary = "发车")
    @PostMapping("/{id}/depart")
    public ResponseEntity<String> depart(@PathVariable Long id) {
        transportOrderService.depart(id);
        return ResponseEntity.ok("发车成功");
    }

    @Operation(summary = "到达确认")
    @PostMapping("/{id}/arrive")
    public ResponseEntity<String> arrive(@PathVariable Long id) {
        transportOrderService.arrive(id);
        return ResponseEntity.ok("到达确认成功");
    }

    @Operation(summary = "标记异常")
    @PostMapping("/{id}/exception")
    public ResponseEntity<String> markException(@PathVariable Long id, @RequestParam String remark) {
        transportOrderService.markException(id, remark);
        return ResponseEntity.ok("已标记异常");
    }

    @Operation(summary = "取消运输单")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<String> cancel(@PathVariable Long id, @RequestParam String reason) {
        transportOrderService.cancel(id, reason);
        return ResponseEntity.ok("取消成功");
    }
}
