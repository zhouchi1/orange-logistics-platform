package com.orange.logistics.waybill.controller;

import com.orange.logistics.waybill.dto.AddTraceDTO;
import com.orange.logistics.waybill.dto.GenerateWaybillDTO;
import com.orange.logistics.waybill.dto.SignConfirmDTO;
import com.orange.logistics.waybill.service.WaybillService;
import com.orange.logistics.waybill.vo.WaybillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "运单管理", description = "运单生成、轨迹管理、签收确认")
@RestController
@RequestMapping("/api/waybill")
@RequiredArgsConstructor
public class WaybillController {

    private final WaybillService waybillService;

    @Operation(summary = "生成运单（电子面单）")
    @PostMapping
    public ResponseEntity<WaybillVO> generateWaybill(@Valid @RequestBody GenerateWaybillDTO dto) {
        return ResponseEntity.ok(waybillService.generateWaybill(dto));
    }

    @Operation(summary = "内部调用-生成运单")
    @PostMapping("/generate")
    public Map<String, Object> generateWaybillInternal(@RequestBody Map<String, Object> params) {
        return waybillService.generateWaybillFromMap(params);
    }

    @Operation(summary = "根据运单号查询")
    @GetMapping("/no/{waybillNo}")
    public ResponseEntity<WaybillVO> getByWaybillNo(@PathVariable String waybillNo) {
        return ResponseEntity.ok(waybillService.getByWaybillNo(waybillNo));
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public ResponseEntity<WaybillVO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(waybillService.getById(id));
    }

    @Operation(summary = "添加运单轨迹")
    @PostMapping("/trace")
    public ResponseEntity<String> addTrace(@RequestBody AddTraceDTO dto) {
        waybillService.addTrace(dto);
        return ResponseEntity.ok("轨迹添加成功");
    }

    @Operation(summary = "签收确认")
    @PostMapping("/sign")
    public ResponseEntity<String> confirmSign(@RequestBody SignConfirmDTO dto) {
        waybillService.confirmSign(dto);
        return ResponseEntity.ok("签收确认成功");
    }

    @Operation(summary = "根据订单ID查询运单")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<WaybillVO>> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(waybillService.getByOrderId(orderId));
    }
}
