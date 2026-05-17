package com.orange.logistics.dispatch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.DispatchRequestDTO;
import com.orange.logistics.dispatch.entity.DeliveryTask;
import com.orange.logistics.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "智能派单", description = "智能派单、任务管理、配送方式选择")
@RestController
@RequestMapping("/api/dispatch/task")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @Operation(summary = "智能派单")
    @PostMapping("/auto")
    public ResponseEntity<DeliveryTask> dispatch(@Valid @RequestBody DispatchRequestDTO dto) {
        return ResponseEntity.ok(dispatchService.dispatch(dto));
    }

    @Operation(summary = "手动指派")
    @PostMapping("/{taskId}/assign/{courierId}")
    public ResponseEntity<DeliveryTask> manualAssign(@PathVariable Long taskId, @PathVariable Long courierId) {
        return ResponseEntity.ok(dispatchService.manualAssign(taskId, courierId));
    }

    @Operation(summary = "取件确认")
    @PostMapping("/{taskId}/pickup")
    public ResponseEntity<String> pickup(@PathVariable Long taskId) {
        dispatchService.pickup(taskId);
        return ResponseEntity.ok("取件成功");
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryTask> getById(@PathVariable Long id) {
        return ResponseEntity.ok(dispatchService.getById(id));
    }

    @Operation(summary = "分页查询配送任务")
    @GetMapping("/page")
    public ResponseEntity<Page<DeliveryTask>> pageTasks(
            @RequestParam(required = false) Long courierId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(dispatchService.pageTasks(courierId, status, page, size));
    }

    @Operation(summary = "修改配送方式")
    @PutMapping("/{taskId}/method")
    public ResponseEntity<String> changeDeliveryMethod(
            @PathVariable Long taskId,
            @RequestParam Integer method,
            @RequestParam(required = false) String deliveryPoint) {
        dispatchService.changeDeliveryMethod(taskId, method, deliveryPoint);
        return ResponseEntity.ok("配送方式已修改");
    }
}
