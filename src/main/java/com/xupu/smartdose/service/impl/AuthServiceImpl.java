package com.xupu.smartdose.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xupu.smartdose.dto.LoginDTO;
import com.xupu.smartdose.dto.LoginVO;
import com.xupu.smartdose.entity.SysUser;
import com.xupu.smartdose.mapper.UserMapper;
import com.xupu.smartdose.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    /** token → username (内存存储，重启失效) */
    private static final Map<String, String> TOKEN_STORE = new ConcurrentHashMap<>();

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, dto.getUsername())
                        .eq(SysUser::getEnabled, 1)
        );
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        String md5 = md5Hex(dto.getPassword());
        if (!md5.equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        TOKEN_STORE.put(token, user.getUsername());
        return new LoginVO(user.getUsername(), user.getNickname(), token);
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            TOKEN_STORE.remove(token);
        }
    }

    /** 根据 token 查询用户名，用于接口鉴权（可选） */
    public static String getUsernameByToken(String token) {
        return token == null ? null : TOKEN_STORE.get(token);
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
