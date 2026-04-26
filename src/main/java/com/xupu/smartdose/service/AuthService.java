package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.LoginDTO;
import com.xupu.smartdose.dto.LoginVO;

public interface AuthService {
    LoginVO login(LoginDTO dto);
    void logout(String token);
}
