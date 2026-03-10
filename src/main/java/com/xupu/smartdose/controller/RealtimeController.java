package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeService realtimeService;

    /**
     * GET /api/realtime/water-quality
     * 返回出水水质实时值（指标卡片）+ 24小时图表数据
     */
    @GetMapping("/water-quality")
    public Result<WaterQualityDTO> getWaterQuality() {
        return Result.success(realtimeService.getOutWaterData());
    }

    /**
     * GET /api/realtime/chart?param=nh3n&startTime=...&endTime=...
     * 按指定参数和时间范围返回图表数据
     */
    @GetMapping("/chart")
    public Result<WaterQualityDTO.ChartData> getChart(
            @RequestParam(defaultValue = "nh3n") String param,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(realtimeService.getChartData(param, startTime, endTime));
    }
}
