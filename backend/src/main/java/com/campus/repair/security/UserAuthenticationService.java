package com.campus.repair.security;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.dto.LoginRequest;
import com.campus.repair.entity.UserEntity;
import com.campus.repair.service.UserService;
import com.campus.repair.vo.AuthVO;
import com.campus.repair.vo.UserVO;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserAuthenticationService {
    private final UserService users;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPassword;

    public UserAuthenticationService(UserService users, JwtUtils jwtUtils, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        dummyPassword = passwordEncoder.encode("not-a-real-account-password");
    }

    public AuthVO login(LoginRequest request) {
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        UserEntity user = users.findByUsername(request.getUsername());
        boolean matches = passwordEncoder.matches(request.getPassword(), user == null ? dummyPassword : user.getPassword());
        if (!matches || !isActive(user)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Jwt jwt = jwtUtils.generate(user);
        return new AuthVO(jwt.getTokenValue(), jwt.getExpiresAt(), UserVO.from(user), user.getRole());
    }

    public UserVO authenticate(Jwt jwt) {
        UserEntity user = users.findById(Long.parseLong(jwt.getSubject()));
        Number version = jwt.getClaim("ver");
        if (!isActive(user) || user.getTokenVersion() != version.longValue()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UserVO.from(user);
    }

    public void logout(long id) {
        users.revokeTokens(id);
    }

    private boolean isActive(UserEntity user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && user.getRole() != null;
    }
}
