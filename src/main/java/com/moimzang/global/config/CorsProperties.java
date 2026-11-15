package com.moimzang.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 허용 오리진은 환경마다 달라서 배포 시 환경변수로 주입한다. 기본값은 로컬 개발 주소라
// 운영에서 주입을 빠뜨리면 프론트 호출이 막히는 쪽으로 실패한다(열린 쪽이 아니라)
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {}
