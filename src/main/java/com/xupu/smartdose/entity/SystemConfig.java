package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

/**
 * 系统配置表（通用 key-value 存储）
 *
 * 当前用途：
 *   config_key = "run_mode"  -> config_value = "auto" | "smart"
 */
@Data
@TableName("system_config")
public class SystemConfig {

    /** 配置项键，同时作为主键 */
    @TableId(type = IdType.INPUT)
    private String configKey;

    /** 配置项值 */
    private String configValue;
}
