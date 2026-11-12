package com.ringout.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

    /** 활성 모임방 목록과 방별 인원 수. 모든 사용자에게 같은 값이라 키 1개로 공유한다. */
    public static final String ACTIVE_ROOMS = "activeRooms";

    /** 무효화 이벤트가 없는 활동 정렬 변경과, 놓친 쓰기 경로를 회복하는 상한 */
    private static final Duration ACTIVE_ROOMS_TTL = Duration.ofMinutes(1);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAllowNullValues(false);
        cacheManager.registerCustomCache(ACTIVE_ROOMS, Caffeine.newBuilder()
            .expireAfterWrite(ACTIVE_ROOMS_TTL)
            .maximumSize(1)
            .recordStats()
            .build());
        return cacheManager;
    }
}
