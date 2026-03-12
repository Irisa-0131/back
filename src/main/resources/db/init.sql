-- ============================================================
-- XupuSmartDose 数据库初始化脚本
-- 执行前请先创建数据库：CREATE DATABASE xupu_smartdose DEFAULT CHARSET utf8mb4;
-- ============================================================

USE xupu_smartdose;

-- ------------------------------------------------------------
-- 1. 水质记录表（进水/出水实测值 & 预测值）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS water_quality_record (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    record_time  DATETIME     NOT NULL               COMMENT '记录时间',
    nh3n         DECIMAL(8,3)                        COMMENT 'NH3-N 氨氮 mg/L',
    cod          DECIMAL(8,3)                        COMMENT 'COD 化学需氧量 mg/L',
    tp           DECIMAL(8,3)                        COMMENT 'TP 总磷 mg/L',
    tn           DECIMAL(8,3)                        COMMENT 'TN 总氮 mg/L',
    flow         DECIMAL(10,2)                       COMMENT '流量 m³/d',
    water_type   TINYINT      NOT NULL DEFAULT 1     COMMENT '水质类型 0=进水 1=出水',
    is_predicted TINYINT      NOT NULL DEFAULT 0     COMMENT '0=实测值 1=预测值',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_record_time (record_time),
    INDEX idx_water_type  (water_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水质记录表';

-- ------------------------------------------------------------
-- 2. 加药参数表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dosage_param (
    id                  INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    chemical_type       VARCHAR(20)  NOT NULL               COMMENT '药剂类型 ty/pac/fecl3/pam_neg/pam_pos',
    high_level          DECIMAL(5,2)                        COMMENT '储罐高位 m',
    low_level           DECIMAL(5,2)                        COMMENT '储罐低位 m',
    dilution_ratio      DECIMAL(5,2)                        COMMENT '配药比例',
    dosing_coefficient  DECIMAL(5,2)                        COMMENT '加药系数',
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chemical_type (chemical_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加药参数表';

-- 初始参数默认值
INSERT IGNORE INTO dosage_param (chemical_type, high_level, low_level, dilution_ratio, dosing_coefficient) VALUES
('ty',      0.5, 0.2, 0.2, 3.0),
('pac',     0.5, 0.2, 0.2, 3.0),
('fecl3',   0.5, 0.2, 0.2, 3.0),
('pam_neg', 0.5, 0.2, 0.2, 3.0),
('pam_pos', 0.5, 0.2, 0.2, 3.0);

-- ------------------------------------------------------------
-- 3. 泵设备状态表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pump_status (
    id           INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    pump_code    VARCHAR(20)  NOT NULL               COMMENT '泵编号 如PUMP_01',
    pump_name    VARCHAR(50)                         COMMENT '泵名称',
    remote_mode  TINYINT      NOT NULL DEFAULT 1     COMMENT '1=远程 2=就地',
    auto_mode    TINYINT      NOT NULL DEFAULT 1     COMMENT '1=自动 2=手动',
    run_status   TINYINT      NOT NULL DEFAULT 0     COMMENT '0=停止 1=运行',
    fault_status TINYINT      NOT NULL DEFAULT 0     COMMENT '0=正常 1=故障',
    frequency    DECIMAL(5,2)                        COMMENT '运行频率 Hz',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pump_code (pump_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='泵设备状态表';

-- 初始泵数据
INSERT IGNORE INTO pump_status (pump_code, pump_name, remote_mode, auto_mode, run_status, fault_status, frequency) VALUES
('PUMP_01',  '碳源投加泵A', 1, 1, 1, 0, 35.0),
('PUMP_02',  '碳源投加泵B', 1, 1, 0, 0,  0.0),
('PUMP_03',  'PAC投加泵',   1, 1, 1, 0, 42.5),
('PUMP_04',  'Fecl3投加泵', 1, 1, 0, 0,  0.0),
('PUMP_05',  'PAM-投加泵',  1, 1, 1, 0, 30.0),
('PUMP_06',  'PAM+投加泵',  1, 1, 0, 0,  0.0),
('PUMP_07',  '投加泵07',    1, 1, 1, 0, 38.0),
('PUMP_08',  '投加泵08',    1, 1, 1, 0, 38.0),
('PUMP_09',  '投加泵09',    1, 1, 0, 0,  0.0),
('PUMP_10',  '投加泵10',    1, 1, 1, 0, 32.0),
('PUMP_11',  '投加泵11',    1, 1, 1, 0, 40.0),
('PUMP_12',  '投加泵12',    1, 1, 0, 0,  0.0),
('PUMP_13',  '投加泵13',    1, 1, 1, 0, 28.0),
('PUMP_14',  '投加泵14',    1, 1, 1, 0, 28.0);

-- ------------------------------------------------------------
-- 4. 报警记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarm_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    alarm_time    DATETIME     NOT NULL               COMMENT '报警时间',
    alarm_type    VARCHAR(20)                         COMMENT 'water_quality/device/parameter',
    alarm_level   TINYINT                             COMMENT '1=提示 2=警告 3=严重',
    alarm_source  VARCHAR(50)                         COMMENT '报警来源 如NH3-N/PUMP_01',
    alarm_content VARCHAR(500)                        COMMENT '报警内容',
    is_handled    TINYINT      NOT NULL DEFAULT 0     COMMENT '0=未处理 1=已处理',
    handled_by    VARCHAR(50)                         COMMENT '处理人',
    handle_time   DATETIME                            COMMENT '处理时间',
    handle_note   VARCHAR(200)                        COMMENT '处理备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_alarm_time  (alarm_time),
    INDEX idx_is_handled  (is_handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警记录表';

-- 已存在的数据库补充 handled_by 列（若列已存在则忽略错误）
ALTER TABLE alarm_record ADD COLUMN IF NOT EXISTS handled_by VARCHAR(50) COMMENT '处理人' AFTER is_handled;

-- 模拟几条报警记录（测试用）
INSERT IGNORE INTO alarm_record (id, alarm_time, alarm_type, alarm_level, alarm_source, alarm_content, is_handled) VALUES
(1, NOW() - INTERVAL 2 HOUR, 'water_quality', 2, 'COD', 'COD超标：当前值76.42 mg/L，标准值75.00 mg/L', 0),
(2, NOW() - INTERVAL 1 HOUR, 'device',        1, 'PUMP_04', 'Fecl3投加泵频率反馈异常', 0);

-- ------------------------------------------------------------
-- 5. 离线模拟任务表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS simulation_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no           VARCHAR(20)  NOT NULL               COMMENT '任务编号',
    file_name         VARCHAR(200)                        COMMENT '原始文件名',
    file_path         VARCHAR(500)                        COMMENT '服务器存储路径',
    data_count        INT                                 COMMENT '数据条数',
    sample_rate       VARCHAR(20)                         COMMENT '采样频率',
    missing_rate      DECIMAL(5,2)                        COMMENT '缺失率(%)',
    time_range        VARCHAR(100)                        COMMENT '数据时间范围',
    validation_status TINYINT      NOT NULL DEFAULT 0     COMMENT '1=通过 2=失败',
    validation_msg    VARCHAR(500)                        COMMENT '校验失败原因',
    is_preprocessed   TINYINT      NOT NULL DEFAULT 0     COMMENT '0=否 1=是',
    preprocess_config TEXT                                COMMENT 'JSON预处理配置',
    column_mapping    TEXT                                COMMENT 'JSON列映射关系',
    operator          VARCHAR(50)                         COMMENT '操作人员',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    predict_params    TEXT                                COMMENT 'JSON预测参数',
    predict_result    MEDIUMTEXT                          COMMENT 'JSON预测结果',
    predict_time      DATETIME                            COMMENT '最近一次预测时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线模拟任务表';

-- 如果表已存在，手动执行以下 ALTER 补充字段：
-- ALTER TABLE simulation_task
--   ADD COLUMN predict_params  TEXT       COMMENT 'JSON预测参数',
--   ADD COLUMN predict_result  MEDIUMTEXT COMMENT 'JSON预测结果',
--   ADD COLUMN predict_time    DATETIME   COMMENT '最近一次预测时间';

-- ------------------------------------------------------------
-- 6. 系统配置表（通用 key-value，当前用于存储运行模式）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
    config_key   VARCHAR(50)  NOT NULL COMMENT '配置项键',
    config_value VARCHAR(200)          COMMENT '配置项值',
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始运行模式：自动模式
INSERT IGNORE INTO system_config (config_key, config_value) VALUES ('run_mode', 'auto');
