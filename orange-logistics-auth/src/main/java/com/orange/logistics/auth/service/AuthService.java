package com.orange.logistics.auth.service;

import com.orange.logistics.auth.dto.LoginRequest;
import com.orange.logistics.auth.dto.RegisterRequest;
import com.orange.logistics.auth.vo.LoginVO;
import com.orange.logistics.auth.vo.UserInfoVO;

public interface AuthService {
    LoginVO login(LoginRequest request);
    void register(RegisterRequest request);
    LoginVO refreshToken(String refreshToken);
    void logout(String token);
    UserInfoVO getCurrentUser(Long userId);
}
