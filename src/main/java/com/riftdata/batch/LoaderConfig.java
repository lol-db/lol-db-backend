package com.riftdata.batch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** CSV 적재 설정 등록. 적재 서비스는 테스트에서도 쓰므로 프로필과 관계없이 등록하고, 실행기만 loader 프로필이다. */
@Configuration
@EnableConfigurationProperties(LoaderProperties.class)
public class LoaderConfig {
}
