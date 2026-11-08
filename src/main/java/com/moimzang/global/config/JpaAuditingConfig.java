package com.moimzang.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 생성·수정 시각 자동 기록(CreatedDate, LastModifiedDate 애너테이션) 활성화.
 *
 * 별도 클래스로 둔다 — 메인 클래스에 달면 웹 슬라이스 테스트가
 * 아우디팅 빈을 찾지 못해 깨진다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
