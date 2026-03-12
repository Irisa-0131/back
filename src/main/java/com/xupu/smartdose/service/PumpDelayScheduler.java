package com.xupu.smartdose.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 泵延时调度器
 * 负责管理启泵/停泵延时任务，支持取消和状态查询。
 */
@Slf4j
@Component
public class PumpDelayScheduler {

    private TaskScheduler taskScheduler;

    /** 每个泵当前待执行的延时任务 */
    private final Map<String, ScheduledFuture<?>> pendingFutures = new ConcurrentHashMap<>();

    /** 每个泵当前待执行的指令（START / STOP） */
    private final Map<String, String> pendingCommands = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("pump-delay-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * 调度一个延时任务。
     * 若该泵已有待执行任务，会先取消旧任务再调度新任务。
     *
     * @param pumpCode     泵编号
     * @param delayMinutes 延时分钟数（0 表示立即执行）
     * @param command      指令标识（START / STOP，仅用于状态展示）
     * @param task         实际要执行的动作
     */
    public void schedule(String pumpCode, int delayMinutes, String command, Runnable task) {
        // 取消旧任务
        cancel(pumpCode);

        if (delayMinutes <= 0) {
            // 延时为 0 时直接执行，不进入调度队列
            pendingCommands.remove(pumpCode);
            task.run();
            return;
        }

        Instant triggerTime = Instant.now().plusSeconds((long) delayMinutes * 60);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            pendingFutures.remove(pumpCode);
            pendingCommands.remove(pumpCode);
            log.info("延时任务触发 -> 泵:{} 指令:{}", pumpCode, command);
            try {
                task.run();
            } catch (Exception e) {
                log.error("延时任务执行失败 -> 泵:{} 指令:{}", pumpCode, command, e);
            }
        }, triggerTime);

        pendingFutures.put(pumpCode, future);
        pendingCommands.put(pumpCode, command);
        log.info("延时任务已调度 -> 泵:{} 指令:{} 延时:{}分钟 触发时间:{}", pumpCode, command, delayMinutes, triggerTime);
    }

    /**
     * 取消指定泵的待执行任务（若存在）。
     */
    public void cancel(String pumpCode) {
        ScheduledFuture<?> existing = pendingFutures.remove(pumpCode);
        if (existing != null) {
            existing.cancel(false);
            String old = pendingCommands.remove(pumpCode);
            log.info("延时任务已取消 -> 泵:{} 原指令:{}", pumpCode, old);
        }
    }

    /**
     * 查询指定泵是否有待执行任务，以及是什么指令。
     *
     * @return "START" / "STOP" / null
     */
    public String getPendingCommand(String pumpCode) {
        ScheduledFuture<?> f = pendingFutures.get(pumpCode);
        if (f == null || f.isDone() || f.isCancelled()) {
            pendingFutures.remove(pumpCode);
            pendingCommands.remove(pumpCode);
            return null;
        }
        return pendingCommands.get(pumpCode);
    }
}
