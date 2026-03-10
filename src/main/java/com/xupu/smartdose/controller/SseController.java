package com.xupu.smartdose.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xupu.smartdose.dto.WaterQualityDTO;
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

    private void pushToEmitter(SseEmitter emitter) {
        try {
            WaterQualityDTO data = realtimeService.getOutWaterData();
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name("water-quality")
                    .data(json));
        } catch (Exception e) {
            log.warn("初始推送失败: {}", e.getMessage());
            // 推送空对象让前端退出 loading 状态，避免页面一直转圈
            try {
                emitter.send(SseEmitter.event()
                        .name("water-quality")
                        .data("{}"));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
