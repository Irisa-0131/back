package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.service.PumpService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pump")
@RequiredArgsConstructor
public class PumpController {

    private final PumpService pumpService;

    /**
     * GET /api/pump/status
     * 获取所有泵设备状态列表
     */
    @GetMapping("/status")
    public Result<List<PumpStatus>> getAllStatus() {
        return Result.success(pumpService.getAllPumpStatus());
    }

    /**
     * GET /api/pump/status/{pumpCode}
     * 获取单台泵状态
     */
    @GetMapping("/status/{pumpCode}")
    public Result<PumpStatus> getStatus(@PathVariable String pumpCode) {
        return Result.success(pumpService.getPumpStatus(pumpCode));
    }

    /**
     * POST /api/pump/command
     * 发送控制指令（START / STOP / AUTO / MANUAL）
     */
    @PostMapping("/command")
    public Result<Void> sendCommand(@RequestBody CommandRequest req) {
        pumpService.sendCommand(req.getPumpCode(), req.getCommand());
        return Result.success();
    }

    @Data
    public static class CommandRequest {
        private String pumpCode;
        /** START=启动, STOP=停止, AUTO=切自动, MANUAL=切手动 */
        private String command;
    }
}
