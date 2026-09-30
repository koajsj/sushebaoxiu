package com.campus.repair.security;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.vo.UserVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

// Registered only inside the Spring Security chain, not as a servlet filter bean.
public class TokenFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserAuthenticationService authentication;
    private final JsonSecurityHandler errors;

    public TokenFilter(JwtUtils jwtUtils, UserAuthenticationService authentication, JsonSecurityHandler errors) {
        this.jwtUtils = jwtUtils;
        this.authentication = authentication;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath()))
                || "/api/health".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() > 4096) {
                errors.write(response, ErrorCode.UNAUTHORIZED);
                return;
            }
            try {
                UserVO user = authentication.authenticate(jwtUtils.parse(header.substring(7)));
                var token = UsernamePasswordAuthenticationToken.authenticated(user, null,
                        List.of(new SimpleGrantedAuthority(user.role().authority())));
                token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(token);
            } catch (JwtException | BusinessException exception) {
                SecurityContextHolder.clearContext();
                errors.write(response, ErrorCode.UNAUTHORIZED);
                return;
            } catch (DataAccessException exception) {
                errors.write(response, ErrorCode.DATABASE_UNAVAILABLE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
