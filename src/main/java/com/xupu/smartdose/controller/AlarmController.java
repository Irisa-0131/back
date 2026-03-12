package com.xupu.smartdose.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.AlarmConfigDTO;
import com.xupu.smartdose.entity.AlarmRecord;
import com.xupu.smartdose.service.AlarmService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    /**
     * GET /api/alarm/list?page=1&size=10&isHandled=0
     * 分页查询报警记录，isHandled=0 未处理，1 已处理，不传=全部
     */
    @GetMapping("/list")
    public Result<IPage<AlarmRecord>> list(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    Integer isHandled,
            @RequestParam(required = false)    Integer alarmLevel) {
        return Result.success(alarmService.getAlarmPage(page, size, isHandled, alarmLevel));
    }

    /**
     * GET /api/alarm/count-unhandled
     * 获取未处理报警数量（用于侧边栏徽标）
     */
    @GetMapping("/count-unhandled")
    public Result<Long> countUnhandled() {
        return Result.success(alarmService.countUnhandled());
    }

    /**
     * PUT /api/alarm/handle
     * 标记报警已处理
     */
    @PutMapping("/handle")
    public Result<Void> handle(@RequestBody HandleRequest req) {
        alarmService.handleAlarm(req.getAlarmId(), req.getHandledBy(), req.getNote());
        return Result.success();
    }

    /**
     * GET /api/alarm/config
     * 获取报警配置
     */
    @GetMapping("/config")
    public Result<AlarmConfigDTO> getConfig() {
        return Result.success(alarmService.getAlarmConfig());
    }

    /**
     * PUT /api/alarm/config
     * 保存报警配置
     */
    @PutMapping("/config")
    public Result<Void> saveConfig(@RequestBody AlarmConfigDTO dto) {
        alarmService.saveAlarmConfig(dto);
        return Result.success();
    }

    @Data
    public static class HandleRequest {
        private Long alarmId;
        private String handledBy;
        private String note;
    }
}
