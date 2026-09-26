package com.chris64233.railpath.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 通用 Bean 配置。{@link Clock} 以 Bean 形式注入，便于测试中控制时间。
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
