package com.xupu.smartdose.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String username;
    private String nickname;
    private String token;
}
