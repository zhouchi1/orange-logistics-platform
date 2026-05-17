package com.orange.logistics.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.auth.dto.LoginRequest;
import com.orange.logistics.auth.dto.RegisterRequest;
import com.orange.logistics.auth.entity.SysUser;
import com.orange.logistics.auth.entity.SysUserRole;
import com.orange.logistics.auth.repository.SysUserMapper;
import com.orange.logistics.auth.repository.SysUserRoleMapper;
import com.orange.logistics.auth.service.AuthService;
import com.orange.logistics.auth.service.JwtService;
import com.orange.logistics.auth.vo.LoginVO;
import com.orange.logistics.auth.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final String USER_TOKEN_PREFIX = "auth:user:token:";

    @Override
    public LoginVO login(LoginRequest request) {
        // 查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 获取角色和权限
        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        // 生成Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("permissions", permissions);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername());

        // 缓存Token
        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + user.getId(),
                accessToken,
                2, TimeUnit.HOURS
        );

        // 构建用户信息
        UserInfoVO userInfo = buildUserInfoVO(user, roles, permissions);

        log.info("用户登录成功: {}", user.getUsername());
        return LoginVO.of(accessToken, refreshToken, 7200L, userInfo);
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        // 分配默认角色（普通用户）
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(2L); // 默认普通用户角色
        userRole.setCreateTime(LocalDateTime.now());
        userRoleMapper.insert(userRole);

        log.info("用户注册成功: {}", request.getUsername());
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh Token 已过期，请重新登录");
        }

        Long userId = jwtService.getUserIdFromToken(refreshToken);
        String username = jwtService.getUsernameFromToken(refreshToken);

        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new RuntimeException("用户不存在或已被禁用");
        }

        List<String> roles = userMapper.selectRolesByUserId(userId);
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("permissions", permissions);

        String newAccessToken = jwtService.generateAccessToken(userId, username, claims);
        String newRefreshToken = jwtService.generateRefreshToken(userId, username);

        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + userId,
                newAccessToken,
                2, TimeUnit.HOURS
        );

        UserInfoVO userInfo = buildUserInfoVO(user, roles, permissions);
        return LoginVO.of(newAccessToken, newRefreshToken, 7200L, userInfo);
    }

    @Override
    public void logout(String token) {
        if (jwtService.validateToken(token)) {
            Long userId = jwtService.getUserIdFromToken(token);
            // 将Token加入黑名单
            redisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + token,
                    "1",
                    2, TimeUnit.HOURS
            );
            // 删除用户Token缓存
            redisTemplate.delete(USER_TOKEN_PREFIX + userId);
            log.info("用户登出成功, userId: {}", userId);
        }
    }

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        List<String> roles = userMapper.selectRolesByUserId(userId);
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);
        return buildUserInfoVO(user, roles, permissions);
    }

    private UserInfoVO buildUserInfoVO(SysUser user, List<String> roles, List<String> permissions) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        return vo;
    }
}
