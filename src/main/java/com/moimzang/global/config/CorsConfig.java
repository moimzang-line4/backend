package com.moimzang.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 프론트가 자기 오리진에서 API를 호출하므로 CORS를 열어준다. 인증·쿠키를 쓰지 않는
// 서비스라 allowCredentials는 켜지 않는다 — 켜는 순간 오리진 와일드카드를 못 쓰게 되고
// 얻는 것도 없다
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 파일 다운로드 응답의 Content-Disposition을 프론트에서 읽어 파일명을 쓴다
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }
}
