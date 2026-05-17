package com.orange.logistics.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.order.dto.CreateOrderDTO;
import com.orange.logistics.order.dto.OrderSplitDTO;
import com.orange.logistics.order.service.OrderService;
import com.orange.logistics.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单管理", description = "订单创建、查询、取消、拆分、合并")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping
    public ResponseEntity<OrderVO> createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        return ResponseEntity.ok(orderService.createOrder(dto));
    }

    @Operation(summary = "根据ID查询订单")
    @GetMapping("/{id}")
    public ResponseEntity<OrderVO> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @Operation(summary = "根据订单号查询")
    @GetMapping("/no/{orderNo}")
    public ResponseEntity<OrderVO> getOrderByNo(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.getOrderByNo(orderNo));
    }

    @Operation(summary = "分页查询订单")
    @GetMapping("/page")
    public ResponseEntity<Page<OrderVO>> pageOrders(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.pageOrders(customerId, status, page, size));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable Long id, @RequestParam String reason) {
        orderService.cancelOrder(id, reason);
        return ResponseEntity.ok("取消成功");
    }

    @Operation(summary = "更新订单状态")
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer targetStatus,
            @RequestParam(defaultValue = "系统") String operator,
            @RequestParam(required = false) String remark) {
        orderService.updateOrderStatus(id, targetStatus, operator, remark);
        return ResponseEntity.ok("状态更新成功");
    }

    @Operation(summary = "拆分订单")
    @PostMapping("/split")
    public ResponseEntity<List<OrderVO>> splitOrder(@RequestBody OrderSplitDTO dto) {
        return ResponseEntity.ok(orderService.splitOrder(dto));
    }

    @Operation(summary = "合并订单")
    @PostMapping("/merge")
    public ResponseEntity<OrderVO> mergeOrders(@RequestBody List<Long> orderIds) {
        return ResponseEntity.ok(orderService.mergeOrders(orderIds));
    }

    @Operation(summary = "处理货到付款")
    @PostMapping("/{id}/cod")
    public ResponseEntity<String> processCod(@PathVariable Long id, @RequestParam boolean paid) {
        orderService.processCodPayment(id, paid);
        return ResponseEntity.ok("处理成功");
    }
}
