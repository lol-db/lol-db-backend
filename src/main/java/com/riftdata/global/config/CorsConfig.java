package com.riftdata.global.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 조회 API에 적용하는 공통 CORS 설정입니다. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public CorsConfig(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.map(CorsConfig::validateOrigin)
				.distinct()
				.toArray(String[]::new);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
				.allowedOrigins(allowedOrigins) // 빈 목록이면 다른 출처를 허용하지 않습니다.
				.allowedMethods("GET", "HEAD") // OPTIONS 사전 요청은 Spring이 처리합니다.
				.allowedHeaders("Accept", "Content-Type")
				.allowCredentials(false)
				.maxAge(600); // 사전 요청 결과의 유지 시간은 600초입니다.
	}

	private static String validateOrigin(String origin) {
		URI uri;
		try {
			uri = URI.create(origin);
		} catch (IllegalArgumentException exception) {
			throw invalidOrigin();
		}
		String path = uri.getRawPath();
		boolean http = "http".equalsIgnoreCase(uri.getScheme())
				|| "https".equalsIgnoreCase(uri.getScheme());
		if (!http || uri.getHost() == null || uri.getRawUserInfo() != null
				|| uri.getRawQuery() != null || uri.getRawFragment() != null
				|| (path != null && !path.isEmpty() && !"/".equals(path))
				|| uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
			throw invalidOrigin();
		}
		// 주소 끝의 /를 제거합니다.
		return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
	}

	private static IllegalArgumentException invalidOrigin() {
		return new IllegalArgumentException(
				"CORS_ALLOWED_ORIGINS에는 http(s)://호스트:포트 형식의 주소를 쉼표로 구분해 입력하세요. *와 페이지 경로는 사용할 수 없습니다.");
	}
}
