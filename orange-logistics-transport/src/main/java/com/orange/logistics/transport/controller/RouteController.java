package com.orange.logistics.transport.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.RouteDTO;
import com.orange.logistics.transport.entity.TransportRoute;
import com.orange.logistics.transport.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "线路管理", description = "运输线路规划与管理")
@RestController
@RequestMapping("/api/transport/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @Operation(summary = "创建线路")
    @PostMapping
    public ResponseEntity<TransportRoute> createRoute(@Valid @RequestBody RouteDTO dto) {
        return ResponseEntity.ok(routeService.createRoute(dto));
    }

    @Operation(summary = "更新线路")
    @PutMapping("/{id}")
    public ResponseEntity<TransportRoute> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteDTO dto) {
        return ResponseEntity.ok(routeService.updateRoute(id, dto));
    }

    @Operation(summary = "查询线路详情")
    @GetMapping("/{id}")
    public ResponseEntity<TransportRoute> getById(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.getById(id));
    }

    @Operation(summary = "分页查询线路")
    @GetMapping("/page")
    public ResponseEntity<Page<TransportRoute>> pageRoutes(
            @RequestParam(required = false) String originCity,
            @RequestParam(required = false) String destCity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(routeService.pageRoutes(originCity, destCity, page, size));
    }

    @Operation(summary = "智能线路规划")
    @GetMapping("/plan")
    public ResponseEntity<TransportRoute> planRoute(
            @RequestParam String originCity,
            @RequestParam String destCity) {
        TransportRoute route = routeService.planRoute(originCity, destCity);
        if (route == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(route);
    }

    @Operation(summary = "停用线路")
    @PutMapping("/{id}/disable")
    public ResponseEntity<String> disableRoute(@PathVariable Long id) {
        routeService.disableRoute(id);
        return ResponseEntity.ok("线路已停用");
    }
}
