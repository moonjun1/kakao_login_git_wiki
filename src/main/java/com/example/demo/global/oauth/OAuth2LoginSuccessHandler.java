package com.example.demo.global.oauth;

import com.example.demo.domain.auth.service.AuthService;
import com.example.demo.domain.user.entity.Auth;
import com.example.demo.domain.user.entity.User;
import com.example.demo.domain.user.repository.AuthRepository;
import com.example.demo.global.jwt.JwtTokenProvider;
import com.example.demo.global.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ObjectMapper objectMapper;

    // ✅ 생성자에서 최소한의 의존성만 받음 (순환 참조 방지)
    public OAuth2LoginSuccessHandler(OAuth2AuthorizedClientService authorizedClientService,
                                     ObjectMapper objectMapper) {
        this.authorizedClientService = authorizedClientService;
        this.objectMapper = objectMapper;
    }

    // ✅ 필요한 서비스들은 @Autowired로 주입 (늦은 초기화)
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RedisService redisService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthRepository authRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String kakaoId = oauth2User.getAttribute("id").toString();
        log.info("카카오 로그인 성공: kakaoId = {}", kakaoId);

        try {
            // 1. 카카오 리프레시 토큰 추출
            String kakaoRefreshToken = extractKakaoRefreshToken(authentication);

            // 2. User 테이블에 카카오ID 저장
            User user = authService.loginOrRegister(kakaoId);

            // 3. Auth 테이블에 카카오 리프레시 토큰 저장
            saveKakaoRefreshToken(user, kakaoRefreshToken);

            // 4. 우리 서비스 JWT 토큰 생성
            String ourAccessToken = jwtTokenProvider.generateAccessToken(kakaoId);
            String ourRefreshToken = jwtTokenProvider.generateRefreshTokenWithKakaoId(kakaoId);

            // 5. Redis에 우리 서비스 JWT 토큰 저장
            redisService.saveAccessToken(kakaoId, ourAccessToken);
            redisService.saveRefreshToken(kakaoId, ourRefreshToken);

            // 6. JSON 응답으로 우리 서비스 토큰 반환
            Map<String, Object> tokenResponse = createTokenResponse(user, ourAccessToken, ourRefreshToken);

            // 7. JSON 응답 설정
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(tokenResponse));

            log.info("토큰 저장 완료 - DB(카카오): {}, Redis(우리JWT): 저장됨", kakaoId);

        } catch (Exception e) {
            log.error("OAuth2 로그인 처리 중 오류 발생: {}", e.getMessage(), e);
            handleError(response, "OAuth2 로그인 처리 실패");
        }
    }

    // 카카오 리프레시 토큰 추출
    private String extractKakaoRefreshToken(Authentication authentication) {
        try {
            OAuth2AuthorizedClient authorizedClient = authorizedClientService
                    .loadAuthorizedClient("kakao", authentication.getName());

            if (authorizedClient != null) {
                OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
                if (refreshToken != null) {
                    log.info("카카오 리프레시 토큰 추출 성공");
                    return refreshToken.getTokenValue();
                }
            }
        } catch (Exception e) {
            log.error("카카오 토큰 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    // 카카오 리프레시 토큰 저장
    private void saveKakaoRefreshToken(User user, String kakaoRefreshToken) {
        Optional<Auth> optionalAuth = authRepository.findByUser(user);

        if (optionalAuth.isPresent()) {
            // 기존 사용자 - 토큰 업데이트
            Auth auth = optionalAuth.get();
            if (kakaoRefreshToken != null) {
                auth.updateKakaoRefreshToken(kakaoRefreshToken);
                authRepository.save(auth);
                log.info("기존 사용자 - 카카오 토큰 DB 저장: userId = {}", user.getId());
            }
        } else {
            // 새 사용자 - Auth 엔티티 생성
            Auth newAuth = Auth.builder()
                    .user(user)
                    .refreshKey(kakaoRefreshToken)
                    .build();
            authRepository.save(newAuth);
            user.setAuth(newAuth);
            log.info("새 사용자 - 카카오 토큰 DB 저장: userId = {}", user.getId());
        }
    }

    // 토큰 응답 생성
    private Map<String, Object> createTokenResponse(User user, String accessToken, String refreshToken) {
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("accessToken", accessToken);
        tokenResponse.put("refreshToken", refreshToken);
        tokenResponse.put("tokenType", "Bearer");
        tokenResponse.put("expiresIn", 1800);
        tokenResponse.put("user", Map.of(
                "id", user.getId(),
                "kakaoId", user.getKakaoId(),
                "status", user.getStatus()
        ));
        return tokenResponse;
    }

    // 에러 처리
    private void handleError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "OAuth2 Login Error");
        errorResponse.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}