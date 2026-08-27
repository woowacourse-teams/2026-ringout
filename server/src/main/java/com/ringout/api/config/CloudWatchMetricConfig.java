package com.ringout.api.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration
@Profile("prod")
public class CloudWatchMetricConfig {

    private static final String HTTP_SERVER_REQUESTS_METRIC = "http.server.requests";
    private static final List<String> EXCLUDED_URI_PREFIXES = List.of(
        "/actuator",
        "/api/v1/health",
        "/swagger-ui",
        "/swagger-ui.html",
        "/v3/api-docs",
        "/swagger-resources",
        "/favicon.ico"
    );

    @Bean
    public CloudWatchConfig cloudWatchConfig() {
        return new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return "ringout/Backend";
            }

            @Override
            public Duration step() {
                return Duration.ofMinutes(1);
            }
        };
    }

    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();
    }

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(
        CloudWatchConfig cloudWatchConfig,
        CloudWatchAsyncClient cloudWatchAsyncClient
    ) {
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(
            cloudWatchConfig,
            Clock.SYSTEM,
            cloudWatchAsyncClient
        );
        registry.config()
            .meterFilter(MeterFilter.ignoreTags(
                "error",
                "exception",
                "outcome"
            ))
            .meterFilter(MeterFilter.deny(
                id -> HTTP_SERVER_REQUESTS_METRIC.equals(id.getName())
                    && isExcludedUri(id.getTag("uri"))
            ))
            .meterFilter(MeterFilter.denyUnless(
                id -> HTTP_SERVER_REQUESTS_METRIC.equals(id.getName())
            ));
        return registry;
    }

    private static boolean isExcludedUri(String uri) {
        return uri != null && EXCLUDED_URI_PREFIXES.stream()
            .anyMatch(uri::startsWith);
    }
}
