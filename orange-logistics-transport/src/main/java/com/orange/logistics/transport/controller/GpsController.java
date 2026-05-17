package com.orange.logistics.transport.controller;

import com.orange.logistics.transport.dto.GpsReportDTO;
import com.orange.logistics.transport.dto.TemperatureReportDTO;
import com.orange.logistics.transport.entity.GpsRecord;
import com.orange.logistics.transport.entity.TemperatureRecord;
import com.orange.logistics.transport.service.GpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "GPS与温控", description = "GPS实时位置上报、轨迹查询、冷链温控监控")
@RestController
@RequestMapping("/api/transport/gps")
@RequiredArgsConstructor
public class GpsController {

    private final GpsService gpsService;

    @Operation(summary = "GPS位置上报")
    @PostMapping("/report")
    public ResponseEntity<String> reportGps(@Valid @RequestBody GpsReportDTO dto) {
        gpsService.reportGps(dto);
        return ResponseEntity.ok("上报成功");
    }

    @Operation(summary = "查询车辆轨迹")
    @GetMapping("/track/{vehicleId}")
    public ResponseEntity<List<GpsRecord>> getTrack(
            @PathVariable Long vehicleId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ResponseEntity.ok(gpsService.getTrack(vehicleId, startTime, endTime));
    }

    @Operation(summary = "查询车辆最新位置")
    @GetMapping("/latest/{vehicleId}")
    public ResponseEntity<GpsRecord> getLatestPosition(@PathVariable Long vehicleId) {
        GpsRecord record = gpsService.getLatestPosition(vehicleId);
        if (record == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(record);
    }

    @Operation(summary = "冷链温度上报")
    @PostMapping("/temperature/report")
    public ResponseEntity<String> reportTemperature(@Valid @RequestBody TemperatureReportDTO dto) {
        gpsService.reportTemperature(dto);
        return ResponseEntity.ok("温度上报成功");
    }

    @Operation(summary = "查询运输单温度记录")
    @GetMapping("/temperature/{transportOrderId}")
    public ResponseEntity<List<TemperatureRecord>> getTemperatureHistory(@PathVariable Long transportOrderId) {
        return ResponseEntity.ok(gpsService.getTemperatureHistory(transportOrderId));
    }
}
