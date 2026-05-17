package com.orange.logistics.auth.vo;

import lombok.Data;
import java.util.List;

@Data
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfoVO userInfo;

    public static LoginVO of(String accessToken, String refreshToken, Long expiresIn, UserInfoVO userInfo) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(expiresIn);
        vo.setUserInfo(userInfo);
        return vo;
    }
}
