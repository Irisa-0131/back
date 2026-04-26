package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 用户名 */
    private String username;

    /** 密码（MD5） */
    private String password;

    /** 昵称/显示名 */
    private String nickname;

    /** 0=禁用 1=启用 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
