package com.orange.logistics.transport.service;

import com.orange.logistics.transport.dto.GpsReportDTO;
import com.orange.logistics.transport.dto.TemperatureReportDTO;
import com.orange.logistics.transport.entity.GpsRecord;
import com.orange.logistics.transport.entity.TemperatureRecord;

import java.util.List;

public interface GpsService {
    void reportGps(GpsReportDTO dto);
    List<GpsRecord> getTrack(Long vehicleId, String startTime, String endTime);
    GpsRecord getLatestPosition(Long vehicleId);
    void reportTemperature(TemperatureReportDTO dto);
    List<TemperatureRecord> getTemperatureHistory(Long transportOrderId);
}
