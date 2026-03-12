package com.xupu.smartdose.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xupu.smartdose.dto.PamStatusDTO;
import com.xupu.smartdose.dto.TankLevelDTO;
import com.xupu.smartdose.dto.WaterOverviewDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.plc.PlcDataService;
import com.xupu.smartdose.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final RealtimeService realtimeService;
    private final PlcDataService plcDataService;
    private final ObjectMapper objectMapper;

    /** 保存所有活跃的 SSE 连接 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * GET /api/sse/water-quality
     * 建立 SSE 长连接，服务端每 30 秒推送最新水质数据
     */
    @CrossOrigin(origins = "*")
    @GetMapping("/water-quality")
    public SseEmitter subscribe() {
        // 超时时间设为 1 小时，前端断线会自动重连
        SseEmitter emitter = new SseEmitter(3600_000L);

        emitters.add(emitter);
        log.info("SSE 客户端连接，当前连接数: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE 客户端断开，当前连接数: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        });

        // 连接建立后立即推送一次
        pushToEmitter(emitter);

        return emitter;
    }

    /** 每 30 秒广播一次最新水质数据给所有连接的客户端 */
    @Scheduled(fixedRate = 30000)
    public void pushToAll() {
        if (emitters.isEmpty()) return;

        WaterQualityDTO data;
        try {
            data = realtimeService.getOutWaterData();
        } catch (Exception e) {
            log.error("查询水质数据失败", e);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("序列化水质数据失败", e);
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("water-quality")
                        .data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.debug("SSE 推送完成，活跃连接: {}", emitters.size());
    }

    /** 每 30 秒向所有客户端推送进/出水概览数据 */
    @Scheduled(fixedRate = 30_000)
    public void pushWaterOverviewToAll() {
        if (emitters.isEmpty()) return;
        String json;
        try {
            WaterOverviewDTO overview = realtimeService.getWaterOverview();
            json = objectMapper.writeValueAsString(overview);
        } catch (Exception e) {
            log.error("读取/序列化进出水概览数据失败", e);
            return;
        }
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("water-overview").data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.debug("进出水概览 SSE 推送完成，活跃连接: {}", emitters.size());
    }

    /** 每 60 秒向所有客户端推送最新液位数据 */
    @Scheduled(fixedRate = 60_000)
    public void pushTankLevelToAll() {
        if (emitters.isEmpty()) return;
        String json;
        try {
            List<TankLevelDTO> levels = plcDataService.readAllTankLevels();
            json = objectMapper.writeValueAsString(levels);
        } catch (Exception e) {
            log.error("读取/序列化液位数据失败", e);
            return;
        }
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("tank-level").data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.debug("液位 SSE 推送完成，活跃连接: {}", emitters.size());
    }

    /** 每 10 秒向所有客户端推送 PAM 装置状态（震动器/电加热/压力/无水报警） */
    @Scheduled(fixedRate = 10_000)
    public void pushPamStatusToAll() {
        if (emitters.isEmpty()) return;
        String json;
        try {
            List<PamStatusDTO> status = plcDataService.readPamStatus();
            json = objectMapper.writeValueAsString(status);
        } catch (Exception e) {
            log.error("读取/序列化 PAM 状态失败", e);
            return;
        }
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("pam-status").data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.debug("PAM 状态 SSE 推送完成，活跃连接: {}", emitters.size());
    }

    private void pushToEmitter(SseEmitter emitter) {
        // 推送水质数据
        try {
            WaterQualityDTO data = realtimeService.getOutWaterData();
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name("water-quality").data(json));
        } catch (Exception e) {
            log.warn("初始水质推送失败: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("water-quality").data("{}"));
            } catch (IOException ex) {
                emitters.remove(emitter);
                return;
            }
        }
        // 推送液位数据（连接建立时立即发送一次）
        try {
            List<TankLevelDTO> levels = plcDataService.readAllTankLevels();
            String json = objectMapper.writeValueAsString(levels);
            emitter.send(SseEmitter.event().name("tank-level").data(json));
        } catch (Exception e) {
            log.warn("初始液位推送失败: {}", e.getMessage());
        }
        // 推送进/出水概览数据（连接建立时立即发送一次）
        try {
            WaterOverviewDTO overview = realtimeService.getWaterOverview();
            String json = objectMapper.writeValueAsString(overview);
            emitter.send(SseEmitter.event().name("water-overview").data(json));
        } catch (Exception e) {
            log.warn("初始进出水概览推送失败: {}", e.getMessage());
        }
        // 推送 PAM 装置状态（连接建立时立即发送一次）
        try {
            List<PamStatusDTO> status = plcDataService.readPamStatus();
            String json = objectMapper.writeValueAsString(status);
            emitter.send(SseEmitter.event().name("pam-status").data(json));
        } catch (Exception e) {
            log.warn("初始 PAM 状态推送失败: {}", e.getMessage());
        }
    }
}
