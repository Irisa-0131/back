package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.service.PumpService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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

    private static final Set<String> VALID_COMMANDS = Set.of("START", "STOP", "AUTO", "MANUAL");

    /**
     * POST /api/pump/command
     * 发送控制指令（START / STOP / AUTO / MANUAL）
     */
    @PostMapping("/command")
    public Result<Void> sendCommand(@RequestBody CommandRequest req) {
        if (req.getPumpCode() == null || req.getPumpCode().isBlank()) {
            return Result.fail(400, "泵编号不能为空");
        }
        if (!VALID_COMMANDS.contains(req.getCommand())) {
            return Result.fail(400, "非法指令: " + req.getCommand() + "，合法值：START/STOP/AUTO/MANUAL");
        }
        pumpService.sendCommand(req.getPumpCode(), req.getCommand());
        return Result.success();
    }

    /**
     * DELETE /api/pump/pending/{pumpCode}
     * 取消指定泵当前的延时等待任务
     */
    @DeleteMapping("/pending/{pumpCode}")
    public Result<Void> cancelPending(@PathVariable String pumpCode) {
        pumpService.cancelPending(pumpCode);
        return Result.success();
    }

    /**
     * PUT /api/pump/frequency
     * 手动设定泵的运行频率（仅手动模式下有效）
     * Body: { "pumpCode": "PUMP_01", "frequency": 35.5 }
     */
    @PutMapping("/frequency")
    public Result<Void> setFrequency(@RequestBody FrequencyRequest req) {
        if (req.getPumpCode() == null || req.getPumpCode().isBlank()) {
            return Result.fail(400, "泵编号不能为空");
        }
        if (req.getFrequency() == null
                || req.getFrequency().compareTo(BigDecimal.ZERO) < 0
                || req.getFrequency().compareTo(BigDecimal.valueOf(50)) > 0) {
            return Result.fail(400, "频率须在 0~50 Hz 之间");
        }
        pumpService.setFrequency(req.getPumpCode(), req.getFrequency());
        return Result.success();
    }

    @Data
    public static class CommandRequest {
        private String pumpCode;
        /** START=启动, STOP=停止, AUTO=切自动, MANUAL=切手动 */
        private String command;
    }

    @Data
    public static class FrequencyRequest {
        private String pumpCode;
        private BigDecimal frequency;
    }
}
