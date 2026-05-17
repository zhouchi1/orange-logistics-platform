package com.orange.logistics.dispatch.controller;

import com.orange.logistics.dispatch.dto.SignoffDTO;
import com.orange.logistics.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "签收管理", description = "包裹签收、拒收处理")
@RestController
@RequestMapping("/api/dispatch/signoff")
@RequiredArgsConstructor
public class SignoffController {

    private final DispatchService dispatchService;

    @Operation(summary = "签收/拒收")
    @PostMapping
    public ResponseEntity<String> signoff(@Valid @RequestBody SignoffDTO dto) {
        dispatchService.signoff(dto);
        String msg = Boolean.TRUE.equals(dto.getAccepted()) ? "签收成功" : "拒收已记录";
        return ResponseEntity.ok(msg);
    }
}
